package com.clearledger.dto;

import com.clearledger.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Details of a completed or pending transaction")
public record TransactionResponse(
    Long id,
    String transactionReference,
    Long sourceAccountId,
    String sourceAccountNumber,
    Long destinationAccountId,
    String destinationAccountNumber,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String idempotencyKey,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}
