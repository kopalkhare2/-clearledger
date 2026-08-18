package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "System-wide Double-Entry Trial Balance Audit Report")
public record TrialBalanceReportDto(
    @Schema(description = "Total debits in evaluated window", example = "50000.00")
    BigDecimal totalDebits,

    @Schema(description = "Total credits in evaluated window", example = "50000.00")
    BigDecimal totalCredits,

    @Schema(description = "Whether double-entry invariant SUM(debits) == SUM(credits) holds exactly", example = "true")
    boolean isBalanced,

    @Schema(description = "Total number of ledger entries evaluated", example = "100")
    long entryCount,

    @Schema(description = "Total accounts in system", example = "20")
    long accountCount,

    @Schema(description = "Start of evaluation window (null if all-time)", example = "2026-08-01T00:00:00")
    LocalDateTime from,

    @Schema(description = "End of evaluation window", example = "2026-08-18T23:59:59")
    LocalDateTime to,

    @Schema(description = "Timestamp when trial balance was generated", example = "2026-08-18T18:00:00")
    LocalDateTime generatedAt
) {}
