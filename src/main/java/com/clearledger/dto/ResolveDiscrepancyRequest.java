package com.clearledger.dto;

import com.clearledger.domain.ResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to resolve a reconciliation discrepancy")
public record ResolveDiscrepancyRequest(

    @Schema(description = "Action taken to resolve the discrepancy", example = "APPROVE_FEE_ADJUSTMENT")
    @NotNull(message = "action is required")
    ResolutionAction action,

    @Schema(description = "Identifier/name of the operator resolving the discrepancy", example = "operations-user-1")
    @NotBlank(message = "resolvedBy is required")
    @Size(max = 64, message = "resolvedBy must not exceed 64 characters")
    String resolvedBy,

    @Schema(description = "Detailed notes or business justification for this resolution", example = "Approved monthly interchange fee deduction per processor contract")
    @NotBlank(message = "notes is required")
    @Size(max = 500, message = "notes must not exceed 500 characters")
    String notes
) {}
