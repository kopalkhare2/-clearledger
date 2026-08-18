package com.clearledger.dto;

import com.clearledger.domain.EntryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Single line item on an account ledger statement")
public record AccountStatementItemDto(
    @Schema(description = "Ledger entry ID", example = "101")
    Long ledgerEntryId,

    @Schema(description = "Associated transaction reference", example = "3cf75386-b546-4f3c-9e7c-f5039db0bb6c")
    String transactionReference,

    @Schema(description = "Entry type: DEBIT or CREDIT", example = "CREDIT")
    EntryType entryType,

    @Schema(description = "Transaction amount", example = "1000.00")
    BigDecimal amount,

    @Schema(description = "Reconstructed running balance after this entry was applied", example = "6000.00")
    BigDecimal runningBalance,

    @Schema(description = "Timestamp when ledger entry was recorded", example = "2026-08-18T10:15:30")
    LocalDateTime createdAt
) {}
