package com.clearledger.integration;

import com.clearledger.domain.*;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.dto.IngestSettlementBatchRequest;
import com.clearledger.dto.SettlementRecordItemDto;
import com.clearledger.dto.TransferRequest;
import com.clearledger.repository.*;
import com.clearledger.service.AccountService;
import com.clearledger.service.ReconciliationEngineService;
import com.clearledger.service.SettlementIngestionService;
import com.clearledger.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationIntegrationTest extends BaseIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired TransactionService transactionService;
    @Autowired SettlementIngestionService settlementIngestionService;
    @Autowired ReconciliationEngineService reconciliationEngineService;

    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired UserRepository userRepository;
    @Autowired SettlementBatchRepository settlementBatchRepository;
    @Autowired SettlementRecordRepository settlementRecordRepository;
    @Autowired ReconciliationMatchRepository reconciliationMatchRepository;
    @Autowired ReconciliationResolutionAuditRepository reconciliationResolutionAuditRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private Account source;
    private Account destination;

    @BeforeEach
    void cleanup() {
        reconciliationResolutionAuditRepository.deleteAll();
        reconciliationMatchRepository.deleteAll();
        settlementRecordRepository.deleteAll();
        settlementBatchRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        source = accountService.createAccount(
                new CreateAccountRequest("Alice", "alice_recon@example.com", "INR"));
        destination = accountService.createAccount(
                new CreateAccountRequest("Bob", "bob_recon@example.com", "INR"));

        // Fund source with ₹50,000
        transactionTemplate.execute(status -> {
            Account s = accountRepository.findById(source.getId()).orElseThrow();
            s.credit(new BigDecimal("50000.00"));
            accountRepository.save(s);
            return null;
        });
    }

    /**
     * Comprehensive end-to-end reconciliation test proving:
     * 1. Exact match (Tx 1)
     * 2. Amount mismatch (Tx 2)
     * 3. Fee discrepancy (Tx 3)
     * 4. Unmatched external settlement (Ext orphan)
     * 5. Unmatched internal transaction (Tx 4 not in batch)
     * 6. Re-running reconciliation is 100% idempotent (no duplicate matches)
     * 7. Strict immutability of Phase 1 Account balances, Transactions, and Ledger Entries.
     */
    @Test
    void fullReconciliationFlow_verifiesAllMatchClassificationsAndLedgerImmutability() {
        LocalDateTime testTime = LocalDateTime.now();

        // Step 1: Create 4 Phase 1 transactions
        Transaction tx1 = transactionService.transfer(new TransferRequest(
                source.getId(), destination.getId(), new BigDecimal("1000.00"), "INR", UUID.randomUUID().toString()));
        Transaction tx2 = transactionService.transfer(new TransferRequest(
                source.getId(), destination.getId(), new BigDecimal("2000.00"), "INR", UUID.randomUUID().toString()));
        Transaction tx3 = transactionService.transfer(new TransferRequest(
                source.getId(), destination.getId(), new BigDecimal("3000.00"), "INR", UUID.randomUUID().toString()));
        Transaction tx4 = transactionService.transfer(new TransferRequest(
                source.getId(), destination.getId(), new BigDecimal("4000.00"), "INR", UUID.randomUUID().toString()));

        // Snapshot Phase 1 state before reconciliation
        Account srcBefore = accountRepository.findById(source.getId()).orElseThrow();
        Account dstBefore = accountRepository.findById(destination.getId()).orElseThrow();
        long txCountBefore = transactionRepository.count();
        long ledgerCountBefore = ledgerEntryRepository.count();

        assertThat(srcBefore.getBalance()).isEqualByComparingTo("40000.00");
        assertThat(dstBefore.getBalance()).isEqualByComparingTo("10000.00");
        assertThat(txCountBefore).isEqualTo(4);
        assertThat(ledgerCountBefore).isEqualTo(8);

        // Step 2: Ingest external settlement batch
        // - ext_1: Exact match with tx1 (₹1000, fee=0)
        // - ext_2: Amount mismatch with tx2 (tx2 is ₹2000, external reports ₹1800)
        // - ext_3: Fee discrepancy with tx3 (tx3 is ₹3000, external reports gross ₹3000, fee ₹50)
        // - ext_orphan: Unmatched external (no internal transaction)
        // (tx4 is omitted from batch -> will become UNMATCHED_INTERNAL)
        String batchRef = "BATCH-DAILY-" + UUID.randomUUID();
        var ingestReq = new IngestSettlementBatchRequest(
                batchRef,
                "STRIPE",
                List.of(
                        new SettlementRecordItemDto("ext_1", tx1.getTransactionReference(), new BigDecimal("1000.00"), BigDecimal.ZERO, "INR", testTime),
                        new SettlementRecordItemDto("ext_2", tx2.getTransactionReference(), new BigDecimal("1800.00"), BigDecimal.ZERO, "INR", testTime),
                        new SettlementRecordItemDto("ext_3", tx3.getTransactionReference(), new BigDecimal("3000.00"), new BigDecimal("50.00"), "INR", testTime),
                        new SettlementRecordItemDto("ext_orphan", "NON-EXISTENT-REF-999", new BigDecimal("500.00"), BigDecimal.ZERO, "INR", testTime)
                )
        );

        SettlementBatch batch = settlementIngestionService.ingestBatch(ingestReq);
        assertThat(batch.getId()).isNotNull();

        // Step 3: Run reconciliation engine
        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(batch.getId());

        // Assert 5 match classifications
        assertThat(matches).hasSize(5);

        // 1. Exact Match
        ReconciliationMatch match1 = matches.stream()
                .filter(m -> "ext_1".equals(m.getExternalTxId()))
                .findFirst().orElseThrow();
        assertThat(match1.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(match1.getMatchType()).isEqualTo(MatchType.EXACT);
        assertThat(match1.getDiscrepancyReason()).isNull();
        assertThat(match1.getInternalAmount()).isEqualByComparingTo("1000.00");
        assertThat(match1.getExternalGrossAmount()).isEqualByComparingTo("1000.00");

        // 2. Amount Mismatch
        ReconciliationMatch match2 = matches.stream()
                .filter(m -> "ext_2".equals(m.getExternalTxId()))
                .findFirst().orElseThrow();
        assertThat(match2.getStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
        assertThat(match2.getMatchType()).isEqualTo(MatchType.NONE);
        assertThat(match2.getDiscrepancyReason()).contains("Amount mismatch");

        // 3. Fee Discrepancy
        ReconciliationMatch match3 = matches.stream()
                .filter(m -> "ext_3".equals(m.getExternalTxId()))
                .findFirst().orElseThrow();
        assertThat(match3.getStatus()).isEqualTo(ReconciliationStatus.FEE_DISCREPANCY);
        assertThat(match3.getMatchType()).isEqualTo(MatchType.FEE_ADJUSTED);
        assertThat(match3.getExternalFee()).isEqualByComparingTo("50.00");
        assertThat(match3.getExternalNetAmount()).isEqualByComparingTo("2950.00");

        // 4. Unmatched External
        ReconciliationMatch matchExtOrphan = matches.stream()
                .filter(m -> "ext_orphan".equals(m.getExternalTxId()))
                .findFirst().orElseThrow();
        assertThat(matchExtOrphan.getStatus()).isEqualTo(ReconciliationStatus.UNMATCHED_EXTERNAL);
        assertThat(matchExtOrphan.getInternalTransaction()).isNull();

        // 5. Unmatched Internal (tx4)
        ReconciliationMatch matchTx4 = matches.stream()
                .filter(m -> tx4.getTransactionReference().equals(m.getInternalTxReference()))
                .findFirst().orElseThrow();
        assertThat(matchTx4.getStatus()).isEqualTo(ReconciliationStatus.UNMATCHED_INTERNAL);
        assertThat(matchTx4.getSettlementRecord()).isNull();
        assertThat(matchTx4.getInternalAmount()).isEqualByComparingTo("4000.00");

        // Step 4: Verify Idempotency — Re-running reconciliation produces the exact same results
        List<ReconciliationMatch> matchesReRun = reconciliationEngineService.reconcileBatch(batch.getId());
        assertThat(matchesReRun).hasSize(5);
        assertThat(reconciliationMatchRepository.countByBatchIdAndStatus(batch.getId(), ReconciliationStatus.MATCHED)).isEqualTo(1);
        assertThat(reconciliationMatchRepository.countByBatchIdAndStatus(batch.getId(), ReconciliationStatus.AMOUNT_MISMATCH)).isEqualTo(1);
        assertThat(reconciliationMatchRepository.countByBatchIdAndStatus(batch.getId(), ReconciliationStatus.FEE_DISCREPANCY)).isEqualTo(1);
        assertThat(reconciliationMatchRepository.countByBatchIdAndStatus(batch.getId(), ReconciliationStatus.UNMATCHED_EXTERNAL)).isEqualTo(1);
        assertThat(reconciliationMatchRepository.countByBatchIdAndStatus(batch.getId(), ReconciliationStatus.UNMATCHED_INTERNAL)).isEqualTo(1);

        // Step 5: Verify Strict Ledger Immutability
        Account srcAfter = accountRepository.findById(source.getId()).orElseThrow();
        Account dstAfter = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcBefore.getBalance());
        assertThat(dstAfter.getBalance()).isEqualByComparingTo(dstBefore.getBalance());
        assertThat(transactionRepository.count()).isEqualTo(txCountBefore);
        assertThat(ledgerEntryRepository.count()).isEqualTo(ledgerCountBefore);

        // Check each original transaction unchanged
        Transaction tx1After = transactionRepository.findById(tx1.getId()).orElseThrow();
        assertThat(tx1After.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx1After.getAmount()).isEqualByComparingTo("1000.00");
    }
}
