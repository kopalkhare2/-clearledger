package com.clearledger.dto;

import com.clearledger.domain.ReconciliationStatus;
import com.clearledger.domain.ResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Audit trail record for a reconciliation discrepancy resolution event")
public record ResolutionAuditResponse(
    Long id,
    Long matchId,
    ResolutionAction action,
    String resolvedBy,
    String notes,
    ReconciliationStatus previousStatus,
    ReconciliationStatus newStatus,
    LocalDateTime resolvedAt
) {}
