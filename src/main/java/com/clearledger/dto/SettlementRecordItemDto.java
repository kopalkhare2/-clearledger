package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Single settlement transaction record from external processor/bank")
public record SettlementRecordItemDto(

    @Schema(description = "External processor transaction ID", example = "ch_3N123456789")
    @NotBlank(message = "externalTxId is required")
    @Size(max = 128)
    String externalTxId,

    @Schema(description = "Internal transaction reference (if supplied)", example = "3cf75386-b546-4f3c-9e7c-f5039db0bb6c")
    @Size(max = 36)
    String internalTxReference,

    @Schema(description = "Gross settlement amount", example = "1000.00")
    @NotNull(message = "grossAmount is required")
    @DecimalMin(value = "0.01", message = "grossAmount must be greater than zero")
    @Digits(integer = 17, fraction = 2)
    BigDecimal grossAmount,

    @Schema(description = "Processor fee deducted (default 0.00)", example = "25.00")
    @DecimalMin(value = "0.00", message = "fee must be non-negative")
    @Digits(integer = 17, fraction = 2)
    BigDecimal fee,

    @Schema(description = "ISO 4217 currency code", example = "INR")
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3 uppercase letters")
    String currency,

    @Schema(description = "Settlement timestamp", example = "2026-08-18T10:00:00")
    @NotNull(message = "settlementDate is required")
    LocalDateTime settlementDate
) {}
