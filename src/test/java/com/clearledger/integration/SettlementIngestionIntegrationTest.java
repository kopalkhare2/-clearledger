package com.clearledger.integration;

import com.clearledger.domain.SettlementBatch;
import com.clearledger.domain.SettlementBatchStatus;
import com.clearledger.dto.IngestSettlementBatchRequest;
import com.clearledger.dto.SettlementRecordItemDto;
import com.clearledger.exception.InvalidSettlementBatchException;
import com.clearledger.repository.*;
import com.clearledger.service.SettlementIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementIngestionIntegrationTest extends BaseIntegrationTest {

    @Autowired SettlementIngestionService settlementIngestionService;
    @Autowired SettlementBatchRepository settlementBatchRepository;
    @Autowired SettlementRecordRepository settlementRecordRepository;
    @Autowired ReconciliationMatchRepository reconciliationMatchRepository;
    @Autowired ReconciliationResolutionAuditRepository reconciliationResolutionAuditRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;

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
    }

    @Test
    void ingestBatch_validRecords_persistsBatchAndRecords() {
        String batchRef = "BATCH-" + UUID.randomUUID();
        var req = new IngestSettlementBatchRequest(
                batchRef,
                "STRIPE",
                List.of(
                        new SettlementRecordItemDto("ext_1", "tx_1", new BigDecimal("1000.00"), BigDecimal.ZERO, "INR", LocalDateTime.now()),
                        new SettlementRecordItemDto("ext_2", "tx_2", new BigDecimal("500.00"), new BigDecimal("15.00"), "INR", LocalDateTime.now())
                )
        );

        SettlementBatch batch = settlementIngestionService.ingestBatch(req);

        assertThat(batch.getId()).isNotNull();
        assertThat(batch.getBatchReference()).isEqualTo(batchRef);
        assertThat(batch.getSourceProvider()).isEqualTo("STRIPE");
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.PENDING);
        assertThat(batch.getTotalRecords()).isEqualTo(2);
        assertThat(batch.getTotalAmount()).isEqualByComparingTo("1500.00");

        var records = settlementRecordRepository.findByBatchId(batch.getId());
        assertThat(records).hasSize(2);
        assertThat(records).anyMatch(r -> r.getExternalTxId().equals("ext_1") && r.getNetAmount().compareTo(new BigDecimal("1000.00")) == 0);
        assertThat(records).anyMatch(r -> r.getExternalTxId().equals("ext_2") && r.getNetAmount().compareTo(new BigDecimal("485.00")) == 0);
    }

    @Test
    void ingestBatch_duplicateBatchReference_returnsExistingBatchWithoutDuplicates() {
        String batchRef = "BATCH-DUP-" + UUID.randomUUID();
        var req = new IngestSettlementBatchRequest(
                batchRef,
                "VISA",
                List.of(
                        new SettlementRecordItemDto("ext_10", "tx_10", new BigDecimal("200.00"), BigDecimal.ZERO, "INR", LocalDateTime.now())
                )
        );

        SettlementBatch first = settlementIngestionService.ingestBatch(req);
        SettlementBatch second = settlementIngestionService.ingestBatch(req);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(settlementBatchRepository.count()).isEqualTo(1);
        assertThat(settlementRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void ingestBatch_feeExceedsGross_throwsInvalidSettlementBatch() {
        var req = new IngestSettlementBatchRequest(
                "BATCH-INVALID-" + UUID.randomUUID(),
                "VISA",
                List.of(
                        new SettlementRecordItemDto("ext_inv", "tx_inv", new BigDecimal("100.00"), new BigDecimal("150.00"), "INR", LocalDateTime.now())
                )
        );

        assertThatThrownBy(() -> settlementIngestionService.ingestBatch(req))
                .isInstanceOf(InvalidSettlementBatchException.class);
    }
}
