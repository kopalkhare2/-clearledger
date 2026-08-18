package com.clearledger.unit;

import com.clearledger.domain.*;
import com.clearledger.exception.SettlementBatchNotFoundException;
import com.clearledger.repository.ReconciliationMatchRepository;
import com.clearledger.repository.SettlementBatchRepository;
import com.clearledger.repository.SettlementRecordRepository;
import com.clearledger.repository.TransactionRepository;
import com.clearledger.service.ReconciliationEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationEngineTest {

    @Mock SettlementBatchRepository settlementBatchRepository;
    @Mock SettlementRecordRepository settlementRecordRepository;
    @Mock ReconciliationMatchRepository reconciliationMatchRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks ReconciliationEngineService reconciliationEngineService;

    private SettlementBatch batch;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        batch = new SettlementBatch("BATCH-001", "STRIPE");
        lenient().when(reconciliationMatchRepository.save(any(ReconciliationMatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reconcileBatch_exactMatch_producesMatchedExact() {
        when(settlementBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        SettlementRecord record = new SettlementRecord(
                "ext_1", "tx_ref_100",
                new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"),
                "INR", now
        );
        when(settlementRecordRepository.findByBatchId(1L)).thenReturn(List.of(record));

        User user = new User("Alice", "alice@example.com");
        Account src = new Account(user, "CL1", "INR");
        Account dst = new Account(user, "CL2", "INR");
        Transaction tx = new Transaction("tx_ref_100", src, dst, new BigDecimal("1000.00"), "INR", "idem-1");
        tx.markCompleted();

        when(transactionRepository.findByTransactionReference("tx_ref_100")).thenReturn(Optional.of(tx));
        when(transactionRepository.findAll()).thenReturn(List.of(tx));

        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(1L);

        assertThat(matches).hasSize(1);
        ReconciliationMatch match = matches.get(0);
        assertThat(match.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(match.getMatchType()).isEqualTo(MatchType.EXACT);
        assertThat(match.getDiscrepancyReason()).isNull();
        assertThat(record.getStatus()).isEqualTo(SettlementRecordStatus.MATCHED);
    }

    @Test
    void reconcileBatch_amountMismatch_producesAmountMismatch() {
        when(settlementBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        SettlementRecord record = new SettlementRecord(
                "ext_2", "tx_ref_200",
                new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"),
                "INR", now
        );
        when(settlementRecordRepository.findByBatchId(1L)).thenReturn(List.of(record));

        User user = new User("Alice", "alice@example.com");
        Account src = new Account(user, "CL1", "INR");
        Account dst = new Account(user, "CL2", "INR");
        Transaction tx = new Transaction("tx_ref_200", src, dst, new BigDecimal("900.00"), "INR", "idem-2");
        tx.markCompleted();

        when(transactionRepository.findByTransactionReference("tx_ref_200")).thenReturn(Optional.of(tx));
        when(transactionRepository.findAll()).thenReturn(List.of(tx));

        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(1L);

        assertThat(matches).hasSize(1);
        ReconciliationMatch match = matches.get(0);
        assertThat(match.getStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
        assertThat(match.getMatchType()).isEqualTo(MatchType.NONE);
        assertThat(match.getDiscrepancyReason()).contains("Amount mismatch");
        assertThat(record.getStatus()).isEqualTo(SettlementRecordStatus.DISCREPANCY);
    }

    @Test
    void reconcileBatch_feeDeduction_producesFeeDiscrepancy() {
        when(settlementBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        SettlementRecord record = new SettlementRecord(
                "ext_3", "tx_ref_300",
                new BigDecimal("1000.00"), new BigDecimal("25.00"), new BigDecimal("975.00"),
                "INR", now
        );
        when(settlementRecordRepository.findByBatchId(1L)).thenReturn(List.of(record));

        User user = new User("Alice", "alice@example.com");
        Account src = new Account(user, "CL1", "INR");
        Account dst = new Account(user, "CL2", "INR");
        Transaction tx = new Transaction("tx_ref_300", src, dst, new BigDecimal("1000.00"), "INR", "idem-3");
        tx.markCompleted();

        when(transactionRepository.findByTransactionReference("tx_ref_300")).thenReturn(Optional.of(tx));
        when(transactionRepository.findAll()).thenReturn(List.of(tx));

        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(1L);

        assertThat(matches).hasSize(1);
        ReconciliationMatch match = matches.get(0);
        assertThat(match.getStatus()).isEqualTo(ReconciliationStatus.FEE_DISCREPANCY);
        assertThat(match.getMatchType()).isEqualTo(MatchType.FEE_ADJUSTED);
        assertThat(match.getDiscrepancyReason()).contains("Processor fee");
        assertThat(record.getStatus()).isEqualTo(SettlementRecordStatus.DISCREPANCY);
    }

    @Test
    void reconcileBatch_unmatchedExternal_producesUnmatchedExternal() {
        when(settlementBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        SettlementRecord record = new SettlementRecord(
                "ext_orphan", "non_existent_tx",
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"),
                "INR", now
        );
        when(settlementRecordRepository.findByBatchId(1L)).thenReturn(List.of(record));
        when(transactionRepository.findByTransactionReference("non_existent_tx")).thenReturn(Optional.empty());
        when(transactionRepository.findAll()).thenReturn(List.of());

        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(1L);

        assertThat(matches).hasSize(1);
        ReconciliationMatch match = matches.get(0);
        assertThat(match.getStatus()).isEqualTo(ReconciliationStatus.UNMATCHED_EXTERNAL);
        assertThat(match.getMatchType()).isEqualTo(MatchType.NONE);
        assertThat(match.getInternalTransaction()).isNull();
        assertThat(record.getStatus()).isEqualTo(SettlementRecordStatus.UNMATCHED);
    }

    @Test
    void reconcileBatch_throwsWhenBatchNotFound() {
        when(settlementBatchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reconciliationEngineService.reconcileBatch(999L))
                .isInstanceOf(SettlementBatchNotFoundException.class);
    }
}
