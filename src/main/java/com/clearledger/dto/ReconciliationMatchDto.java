package com.clearledger.dto;

import com.clearledger.domain.MatchType;
import com.clearledger.domain.ReconciliationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Individual reconciliation match result")
public record ReconciliationMatchDto(
    Long id,
    Long batchId,
    Long settlementRecordId,
    Long internalTransactionId,
    String internalTxReference,
    String externalTxId,
    ReconciliationStatus status,
    MatchType matchType,
    String discrepancyReason,
    BigDecimal internalAmount,
    BigDecimal externalGrossAmount,
    BigDecimal externalFee,
    BigDecimal externalNetAmount,
    LocalDateTime reconciledAt
) {}
