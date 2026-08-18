package com.clearledger.controller;

import com.clearledger.domain.ReconciliationResolutionAudit;
import com.clearledger.dto.ResolutionAuditResponse;
import com.clearledger.dto.ResolveDiscrepancyRequest;
import com.clearledger.mapper.AuditMapper;
import com.clearledger.service.DiscrepancyResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation/matches")
@Tag(name = "Discrepancy Resolution", description = "Reconciliation exception resolution and audit history endpoints")
public class DiscrepancyResolutionController {

    private final DiscrepancyResolutionService discrepancyResolutionService;
    private final AuditMapper auditMapper;

    public DiscrepancyResolutionController(DiscrepancyResolutionService discrepancyResolutionService,
                                         AuditMapper auditMapper) {
        this.discrepancyResolutionService = discrepancyResolutionService;
        this.auditMapper = auditMapper;
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve discrepancy", description = "Idempotently resolves a reconciliation discrepancy and appends to the immutable audit trail")
    public ResponseEntity<ResolutionAuditResponse> resolveDiscrepancy(
            @PathVariable("id") Long matchId,
            @Valid @RequestBody ResolveDiscrepancyRequest request) {
        ReconciliationResolutionAudit audit = discrepancyResolutionService.resolveDiscrepancy(matchId, request);
        return ResponseEntity.ok(auditMapper.toResolutionResponse(audit));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get resolution history", description = "Retrieves the immutable audit trail of all resolution actions for a given match")
    public ResponseEntity<List<ResolutionAuditResponse>> getResolutionHistory(@PathVariable("id") Long matchId) {
        List<ReconciliationResolutionAudit> history = discrepancyResolutionService.getResolutionHistory(matchId);
        return ResponseEntity.ok(auditMapper.toResolutionResponses(history));
    }
}
