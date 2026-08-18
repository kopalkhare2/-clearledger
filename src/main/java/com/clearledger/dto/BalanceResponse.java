package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Current balance of an account")
public record BalanceResponse(
    Long accountId,
    String currency,
    BigDecimal balance
) {}
