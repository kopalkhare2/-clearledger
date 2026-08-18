package com.clearledger.service;

import com.clearledger.domain.*;
import com.clearledger.exception.SettlementBatchNotFoundException;
import com.clearledger.repository.*;
import com.clearledger.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReconciliationEngineService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEngineService.class);

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final ReconciliationMatchRepository reconciliationMatchRepository;
    private final TransactionRepository transactionRepository;

    public ReconciliationEngineService(SettlementBatchRepository settlementBatchRepository,
                                       SettlementRecordRepository settlementRecordRepository,
                                       ReconciliationMatchRepository reconciliationMatchRepository,
                                       TransactionRepository transactionRepository) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementRecordRepository = settlementRecordRepository;
        this.reconciliationMatchRepository = reconciliationMatchRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Executes deterministic two-way reconciliation between internal transactions and external settlement records.
     * Guaranteed to be atomic and idempotent.
     * Does NOT modify Phase 1 transactions, accounts, or ledger entries.
     */
    @Transactional
    public List<ReconciliationMatch> reconcileBatch(Long batchId) {
        SettlementBatch batch = settlementBatchRepository.findById(batchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(batchId));

        batch.markProcessing();
        List<SettlementRecord> records = settlementRecordRepository.findByBatchId(batchId);

        Set<Long> matchedInternalTxIds = new HashSet<>();
        List<ReconciliationMatch> results = new ArrayList<>();

        LocalDateTime minDate = null;
        LocalDateTime maxDate = null;

        for (SettlementRecord record : records) {
            if (minDate == null || record.getSettlementDate().isBefore(minDate)) {
                minDate = record.getSettlementDate();
            }
            if (maxDate == null || record.getSettlementDate().isAfter(maxDate)) {
                maxDate = record.getSettlementDate();
            }

            ReconciliationMatch match = reconcileSingleRecord(batch, record);
            if (match.getInternalTransaction() != null) {
                matchedInternalTxIds.add(match.getInternalTransaction().getId());
            }
            results.add(match);
        }

        // Check for UNMATCHED_INTERNAL transactions within the batch window
        if (minDate != null && maxDate != null) {
            final LocalDateTime startWindow = minDate.minusDays(1);
            final LocalDateTime endWindow = maxDate.plusDays(1);

            List<Transaction> candidateTransactions = transactionRepository.findAll().stream()
                    .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                    .filter(t -> t.getCreatedAt() != null &&
                            !t.getCreatedAt().isBefore(startWindow) &&
                            !t.getCreatedAt().isAfter(endWindow))
                    .filter(t -> !matchedInternalTxIds.contains(t.getId()))
                    .toList();

            for (Transaction internalTx : candidateTransactions) {
                ReconciliationMatch unmatchedInternal = upsertUnmatchedInternal(batch, internalTx);
                results.add(unmatchedInternal);
            }
        }

        batch.markReconciled();
        settlementBatchRepository.save(batch);

        log.info("Reconciliation completed for batch reference={} totalMatches={}",
                batch.getBatchReference(), results.size());

        return results;
    }

    private ReconciliationMatch reconcileSingleRecord(SettlementBatch batch, SettlementRecord record) {
        String internalRef = record.getInternalTxReference();
        Optional<Transaction> internalTxOpt = (internalRef != null && !internalRef.isBlank())
                ? transactionRepository.findByTransactionReference(internalRef)
                : Optional.empty();

        ReconciliationStatus status;
        MatchType matchType;
        String reason = null;
        Transaction internalTx = null;
        BigDecimal internalAmount = null;

        if (internalTxOpt.isEmpty()) {
            status = ReconciliationStatus.UNMATCHED_EXTERNAL;
            matchType = MatchType.NONE;
            reason = "No matching internal transaction found for reference: " + internalRef;
            record.markUnmatched();
        } else {
            internalTx = internalTxOpt.get();
            internalAmount = internalTx.getAmount();

            if (!internalTx.getCurrency().equalsIgnoreCase(record.getCurrency())) {
                status = ReconciliationStatus.AMOUNT_MISMATCH;
                matchType = MatchType.NONE;
                reason = String.format("Currency mismatch: internal=%s vs external=%s",
                        internalTx.getCurrency(), record.getCurrency());
                record.markDiscrepancy();
            } else if (!Money.equals(internalTx.getAmount(), record.getGrossAmount())) {
                status = ReconciliationStatus.AMOUNT_MISMATCH;
                matchType = MatchType.NONE;
                reason = String.format("Amount mismatch: internal=%s vs external=%s",
                        internalTx.getAmount(), record.getGrossAmount());
                record.markDiscrepancy();
            } else if (record.getFee().compareTo(BigDecimal.ZERO) > 0) {
                status = ReconciliationStatus.FEE_DISCREPANCY;
                matchType = MatchType.FEE_ADJUSTED;
                reason = String.format("Processor fee of %s %s deducted (net settled=%s)",
                        record.getFee(), record.getCurrency(), record.getNetAmount());
                record.markDiscrepancy();
            } else {
                status = ReconciliationStatus.MATCHED;
                matchType = MatchType.EXACT;
                reason = null;
                record.markMatched();
            }
        }

        settlementRecordRepository.save(record);

        // Idempotent upsert of ReconciliationMatch by (batchId, settlementRecordId)
        Optional<ReconciliationMatch> existingMatch =
                reconciliationMatchRepository.findByBatchIdAndSettlementRecordId(batch.getId(), record.getId());

        ReconciliationMatch match;
        if (existingMatch.isPresent()) {
            match = existingMatch.get();
            match.setInternalTransaction(internalTx);
            match.setInternalTxReference(internalRef);
            match.setExternalTxId(record.getExternalTxId());
            match.setStatus(status);
            match.setMatchType(matchType);
            match.setDiscrepancyReason(reason);
            match.setInternalAmount(internalAmount);
            match.setExternalGrossAmount(record.getGrossAmount());
            match.setExternalFee(record.getFee());
            match.setExternalNetAmount(record.getNetAmount());
            match.setReconciledAt(LocalDateTime.now());
        } else {
            match = new ReconciliationMatch(
                    batch,
                    record,
                    internalTx,
                    internalRef,
                    record.getExternalTxId(),
                    status,
                    matchType,
                    reason,
                    internalAmount,
                    record.getGrossAmount(),
                    record.getFee(),
                    record.getNetAmount()
            );
        }

        return reconciliationMatchRepository.save(match);
    }

    private ReconciliationMatch upsertUnmatchedInternal(SettlementBatch batch, Transaction internalTx) {
        Optional<ReconciliationMatch> existing =
                reconciliationMatchRepository.findByBatchIdAndInternalTransactionId(batch.getId(), internalTx.getId());

        ReconciliationMatch match;
        if (existing.isPresent()) {
            match = existing.get();
            match.setStatus(ReconciliationStatus.UNMATCHED_INTERNAL);
            match.setMatchType(MatchType.NONE);
            match.setDiscrepancyReason("Internal transaction was not reported in settlement batch " + batch.getBatchReference());
            match.setInternalAmount(internalTx.getAmount());
            match.setReconciledAt(LocalDateTime.now());
        } else {
            match = new ReconciliationMatch(
                    batch,
                    null,
                    internalTx,
                    internalTx.getTransactionReference(),
                    null,
                    ReconciliationStatus.UNMATCHED_INTERNAL,
                    MatchType.NONE,
                    "Internal transaction was not reported in settlement batch " + batch.getBatchReference(),
                    internalTx.getAmount(),
                    null,
                    null,
                    null
            );
        }

        return reconciliationMatchRepository.save(match);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationMatch> getBatchMatches(Long batchId) {
        return reconciliationMatchRepository.findByBatchId(batchId);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationMatch> getDiscrepancies(Pageable pageable) {
        return reconciliationMatchRepository.findByStatuses(
                List.of(ReconciliationStatus.AMOUNT_MISMATCH,
                        ReconciliationStatus.FEE_DISCREPANCY,
                        ReconciliationStatus.UNMATCHED_INTERNAL,
                        ReconciliationStatus.UNMATCHED_EXTERNAL),
                pageable
        );
    }
}
