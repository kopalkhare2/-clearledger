package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Account statement with opening balance, chronological running balance entries, and closing balance")
public record AccountStatementResponse(
    @Schema(description = "Account ID", example = "1")
    Long accountId,

    @Schema(description = "Account Number", example = "CL1234567890ABCD")
    String accountNumber,

    @Schema(description = "Currency", example = "INR")
    String currency,

    @Schema(description = "Reconstructed balance immediately prior to the 'from' timestamp", example = "5000.00")
    BigDecimal openingBalance,

    @Schema(description = "Calculated closing balance: openingBalance + totalCredits - totalDebits", example = "7500.00")
    BigDecimal closingBalance,

    @Schema(description = "Sum of all debit entries in the window", example = "1000.00")
    BigDecimal totalDebited,

    @Schema(description = "Sum of all credit entries in the window", example = "3500.00")
    BigDecimal totalCredited,

    @Schema(description = "Start of statement window", example = "2026-08-01T00:00:00")
    LocalDateTime from,

    @Schema(description = "End of statement window", example = "2026-08-18T23:59:59")
    LocalDateTime to,

    @Schema(description = "Whether closing balance was verified against the current persisted Account balance", example = "true")
    boolean isClosingBalanceVerified,

    @Schema(description = "Chronological list of ledger statement items")
    List<AccountStatementItemDto> entries
) {}
