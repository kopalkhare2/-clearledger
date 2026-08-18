package com.clearledger.dto;

import com.clearledger.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Account details returned by the API")
public record AccountResponse(
    Long id,
    String accountNumber,
    String currency,
    BigDecimal balance,
    AccountStatus status,
    UserSummary user,
    LocalDateTime createdAt
) {
    @Schema(description = "Minimal user information attached to an account")
    public record UserSummary(Long id, String name, String email) {}
}
