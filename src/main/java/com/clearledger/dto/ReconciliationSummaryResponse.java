package com.clearledger.dto;

import com.clearledger.domain.SettlementBatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Reconciliation summary and detailed match breakdown")
public record ReconciliationSummaryResponse(
    Long batchId,
    String batchReference,
    SettlementBatchStatus batchStatus,
    int totalProcessed,
    int matchedCount,
    int amountMismatchCount,
    int feeDiscrepancyCount,
    int unmatchedInternalCount,
    int unmatchedExternalCount,
    BigDecimal totalInternalAmount,
    BigDecimal totalSettledGrossAmount,
    BigDecimal totalFees,
    BigDecimal totalSettledNetAmount,
    LocalDateTime reconciledAt,
    List<ReconciliationMatchDto> matches
) {}
