package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(description = "Request to transfer funds between two accounts")
public record TransferRequest(

    @Schema(description = "Source account ID", example = "1")
    @NotNull(message = "sourceAccountId is required")
    Long sourceAccountId,

    @Schema(description = "Destination account ID", example = "2")
    @NotNull(message = "destinationAccountId is required")
    Long destinationAccountId,

    @Schema(description = "Transfer amount (positive, 2dp)", example = "1000.00")
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
    BigDecimal amount,

    @Schema(description = "ISO 4217 currency code", example = "INR")
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be uppercase letters")
    String currency,

    @Schema(description = "Client-generated idempotency key (UUID recommended)", example = "a1b2c3d4-...")
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 255)
    String idempotencyKey
) {}
