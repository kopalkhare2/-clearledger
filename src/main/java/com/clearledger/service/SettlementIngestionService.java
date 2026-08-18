package com.clearledger.service;

import com.clearledger.domain.SettlementBatch;
import com.clearledger.domain.SettlementRecord;
import com.clearledger.dto.IngestSettlementBatchRequest;
import com.clearledger.dto.SettlementRecordItemDto;
import com.clearledger.exception.InvalidSettlementBatchException;
import com.clearledger.repository.SettlementBatchRepository;
import com.clearledger.repository.SettlementRecordRepository;
import com.clearledger.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class SettlementIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SettlementIngestionService.class);

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementRecordRepository settlementRecordRepository;

    public SettlementIngestionService(SettlementBatchRepository settlementBatchRepository,
                                      SettlementRecordRepository settlementRecordRepository) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementRecordRepository = settlementRecordRepository;
    }

    /**
     * Atomically ingests an external settlement batch.
     * Idempotent: If a batch with the same batchReference already exists, returns it without creating duplicate records.
     */
    @Transactional
    public SettlementBatch ingestBatch(IngestSettlementBatchRequest request) {
        // Idempotency check on batchReference
        Optional<SettlementBatch> existing = settlementBatchRepository.findByBatchReference(request.batchReference());
        if (existing.isPresent()) {
            log.info("Settlement batch already ingested (idempotent lookup): reference={}", request.batchReference());
            return existing.get();
        }

        if (request.records() == null || request.records().isEmpty()) {
            throw new InvalidSettlementBatchException("Settlement batch must contain at least one record");
        }

        SettlementBatch batch = new SettlementBatch(request.batchReference(), request.sourceProvider());
        BigDecimal totalGross = BigDecimal.ZERO;
        Set<String> seenExternalIds = new HashSet<>();

        for (SettlementRecordItemDto item : request.records()) {
            if (!seenExternalIds.add(item.externalTxId())) {
                throw new InvalidSettlementBatchException("Duplicate externalTxId in batch: " + item.externalTxId());
            }

            BigDecimal gross = Money.of(item.grossAmount());
            BigDecimal fee = item.fee() != null ? Money.of(item.fee()) : BigDecimal.ZERO;

            if (!Money.isPositive(gross)) {
                throw new InvalidSettlementBatchException("Gross amount must be positive for external ID: " + item.externalTxId());
            }
            if (fee.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSettlementBatchException("Fee cannot be negative for external ID: " + item.externalTxId());
            }
            if (fee.compareTo(gross) > 0) {
                throw new InvalidSettlementBatchException("Fee cannot exceed gross amount for external ID: " + item.externalTxId());
            }

            BigDecimal net = gross.subtract(fee);

            SettlementRecord record = new SettlementRecord(
                    item.externalTxId(),
                    item.internalTxReference(),
                    gross,
                    fee,
                    net,
                    item.currency().toUpperCase(),
                    item.settlementDate()
            );

            batch.addRecord(record);
            totalGross = totalGross.add(gross);
        }

        batch.setTotalRecords(request.records().size());
        batch.setTotalAmount(totalGross);

        SettlementBatch saved = settlementBatchRepository.save(batch);
        log.info("Successfully ingested settlement batch reference={} id={} totalRecords={} totalAmount={}",
                saved.getBatchReference(), saved.getId(), saved.getTotalRecords(), saved.getTotalAmount());

        return saved;
    }
}
