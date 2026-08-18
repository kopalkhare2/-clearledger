package com.clearledger.dto;

import com.clearledger.domain.SettlementBatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Settlement batch details response")
public record SettlementBatchResponse(
    Long id,
    String batchReference,
    String sourceProvider,
    SettlementBatchStatus status,
    Integer totalRecords,
    BigDecimal totalAmount,
    LocalDateTime createdAt,
    LocalDateTime reconciledAt
) {}
