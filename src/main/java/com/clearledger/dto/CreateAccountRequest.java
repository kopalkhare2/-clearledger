package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Request to create a new account. Creates the user if the email is new.")
public record CreateAccountRequest(

    @Schema(description = "Full name of the account holder", example = "Kopal Khare")
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @Schema(description = "Email address (identifies the user)", example = "kopal@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    String email,

    @Schema(description = "ISO 4217 currency code", example = "INR")
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be uppercase letters")
    String currency
) {}
