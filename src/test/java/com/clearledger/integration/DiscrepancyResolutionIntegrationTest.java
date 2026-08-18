package com.clearledger.integration;

import com.clearledger.domain.*;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.dto.IngestSettlementBatchRequest;
import com.clearledger.dto.ResolveDiscrepancyRequest;
import com.clearledger.dto.SettlementRecordItemDto;
import com.clearledger.dto.TransferRequest;
import com.clearledger.repository.*;
import com.clearledger.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyResolutionIntegrationTest extends BaseIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired TransactionService transactionService;
    @Autowired SettlementIngestionService settlementIngestionService;
    @Autowired ReconciliationEngineService reconciliationEngineService;
    @Autowired DiscrepancyResolutionService discrepancyResolutionService;

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
                new CreateAccountRequest("Alice", "alice_res@example.com", "INR"));
        destination = accountService.createAccount(
                new CreateAccountRequest("Bob", "bob_res@example.com", "INR"));

        transactionTemplate.execute(status -> {
            Account s = accountRepository.findById(source.getId()).orElseThrow();
            s.credit(new BigDecimal("20000.00"));
            accountRepository.save(s);
            return null;
        });
    }

    @Test
    void resolutionWorkflow_resolvesDiscrepanciesIdempotentlyAndPreservesLedger() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Create 2 Phase 1 transfers
        Transaction tx1 = transactionService.transfer(new TransferRequest(
                source.getId(), destination.getId(), new BigDecimal("1000.00"), "INR", UUID.randomUUID().toString()));
        Transaction tx2 = transactionService.transfer(new TransferRequest(
                source.getId(), destination.getId(), new BigDecimal("2000.00"), "INR", UUID.randomUUID().toString()));

        // Snapshot Phase 1 state
        Account srcBefore = accountRepository.findById(source.getId()).orElseThrow();
        Account dstBefore = accountRepository.findById(destination.getId()).orElseThrow();
        long txCountBefore = transactionRepository.count();
        long ledgerCountBefore = ledgerEntryRepository.count();

        // 2. Ingest and reconcile batch with fee discrepancy and amount mismatch
        String batchRef = "BATCH-RES-" + UUID.randomUUID();
        var ingestReq = new IngestSettlementBatchRequest(
                batchRef, "STRIPE",
                List.of(
                        new SettlementRecordItemDto("ext_fee", tx1.getTransactionReference(), new BigDecimal("1000.00"), new BigDecimal("30.00"), "INR", now),
                        new SettlementRecordItemDto("ext_amt", tx2.getTransactionReference(), new BigDecimal("1900.00"), BigDecimal.ZERO, "INR", now),
                        new SettlementRecordItemDto("ext_orph", "NON_EXISTENT_999", new BigDecimal("500.00"), BigDecimal.ZERO, "INR", now)
                )
        );
        SettlementBatch batch = settlementIngestionService.ingestBatch(ingestReq);
        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(batch.getId());

        ReconciliationMatch feeMatch = matches.stream().filter(m -> m.getStatus() == ReconciliationStatus.FEE_DISCREPANCY).findFirst().orElseThrow();
        ReconciliationMatch amtMatch = matches.stream().filter(m -> m.getStatus() == ReconciliationStatus.AMOUNT_MISMATCH).findFirst().orElseThrow();
        ReconciliationMatch orphMatch = matches.stream().filter(m -> m.getStatus() == ReconciliationStatus.UNMATCHED_EXTERNAL).findFirst().orElseThrow();

        // 3. Resolve Fee Discrepancy -> APPROVE_FEE_ADJUSTMENT
        var feeReq = new ResolveDiscrepancyRequest(ResolutionAction.APPROVE_FEE_ADJUSTMENT, "ops-alice", "Standard 3% fee approved");
        ReconciliationResolutionAudit feeAudit = discrepancyResolutionService.resolveDiscrepancy(feeMatch.getId(), feeReq);

        assertThat(feeAudit.getId()).isNotNull();
        assertThat(feeAudit.getPreviousStatus()).isEqualTo(ReconciliationStatus.FEE_DISCREPANCY);
        assertThat(feeAudit.getNewStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
        assertThat(feeAudit.getAction()).isEqualTo(ResolutionAction.APPROVE_FEE_ADJUSTMENT);

        ReconciliationMatch feeMatchAfter = reconciliationMatchRepository.findById(feeMatch.getId()).orElseThrow();
        assertThat(feeMatchAfter.getStatus()).isEqualTo(ReconciliationStatus.RESOLVED);

        // 4. Resolve Amount Mismatch -> ESCALATE_DISPUTE
        var amtReq = new ResolveDiscrepancyRequest(ResolutionAction.ESCALATE_DISPUTE, "ops-bob", "Short settlement under investigation");
        ReconciliationResolutionAudit amtAudit = discrepancyResolutionService.resolveDiscrepancy(amtMatch.getId(), amtReq);

        assertThat(amtAudit.getPreviousStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
        assertThat(amtAudit.getNewStatus()).isEqualTo(ReconciliationStatus.DISPUTED);

        ReconciliationMatch amtMatchAfter = reconciliationMatchRepository.findById(amtMatch.getId()).orElseThrow();
        assertThat(amtMatchAfter.getStatus()).isEqualTo(ReconciliationStatus.DISPUTED);

        // 5. Resolve Orphan -> DISMISS_ORPHAN
        var orphReq = new ResolveDiscrepancyRequest(ResolutionAction.DISMISS_ORPHAN, "ops-alice", "Manual batch orphan dismissed");
        ReconciliationResolutionAudit orphAudit = discrepancyResolutionService.resolveDiscrepancy(orphMatch.getId(), orphReq);
        assertThat(orphAudit.getNewStatus()).isEqualTo(ReconciliationStatus.RESOLVED);

        // 6. Test Resolution Idempotency (repeating exact same action)
        long auditCountBeforeRepeat = reconciliationResolutionAuditRepository.count();
        ReconciliationResolutionAudit repeatFeeAudit = discrepancyResolutionService.resolveDiscrepancy(feeMatch.getId(), feeReq);
        assertThat(repeatFeeAudit.getId()).isEqualTo(feeAudit.getId());
        assertThat(reconciliationResolutionAuditRepository.count()).isEqualTo(auditCountBeforeRepeat);

        // 7. Verify History Retrieval
        List<ReconciliationResolutionAudit> feeHistory = discrepancyResolutionService.getResolutionHistory(feeMatch.getId());
        assertThat(feeHistory).hasSize(1);
        assertThat(feeHistory.get(0).getResolvedBy()).isEqualTo("ops-alice");

        // 8. Invariant: Strict immutability of Phase 1 financial balances and ledger entries
        Account srcAfter = accountRepository.findById(source.getId()).orElseThrow();
        Account dstAfter = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcBefore.getBalance());
        assertThat(dstAfter.getBalance()).isEqualByComparingTo(dstBefore.getBalance());
        assertThat(transactionRepository.count()).isEqualTo(txCountBefore);
        assertThat(ledgerEntryRepository.count()).isEqualTo(ledgerCountBefore);
    }
}
