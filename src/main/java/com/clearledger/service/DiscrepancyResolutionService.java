package com.clearledger.service;

import com.clearledger.domain.ReconciliationMatch;
import com.clearledger.domain.ReconciliationResolutionAudit;
import com.clearledger.domain.ReconciliationStatus;
import com.clearledger.domain.ResolutionAction;
import com.clearledger.dto.ResolveDiscrepancyRequest;
import com.clearledger.exception.InvalidResolutionActionException;
import com.clearledger.exception.ReconciliationMatchNotFoundException;
import com.clearledger.repository.ReconciliationMatchRepository;
import com.clearledger.repository.ReconciliationResolutionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DiscrepancyResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DiscrepancyResolutionService.class);

    private final ReconciliationMatchRepository reconciliationMatchRepository;
    private final ReconciliationResolutionAuditRepository reconciliationResolutionAuditRepository;

    public DiscrepancyResolutionService(ReconciliationMatchRepository reconciliationMatchRepository,
                                       ReconciliationResolutionAuditRepository reconciliationResolutionAuditRepository) {
        this.reconciliationMatchRepository = reconciliationMatchRepository;
        this.reconciliationResolutionAuditRepository = reconciliationResolutionAuditRepository;
    }

    /**
     * Resolves a reconciliation discrepancy idempotently and records an immutable audit trail entry.
     * Does NOT mutate Phase 1 accounts, transactions, or ledger entries.
     */
    @Transactional
    public ReconciliationResolutionAudit resolveDiscrepancy(Long matchId, ResolveDiscrepancyRequest request) {
        ReconciliationMatch match = reconciliationMatchRepository.findById(matchId)
                .orElseThrow(() -> new ReconciliationMatchNotFoundException(matchId));

        ReconciliationStatus targetStatus = (request.action() == ResolutionAction.ESCALATE_DISPUTE)
                ? ReconciliationStatus.DISPUTED
                : ReconciliationStatus.RESOLVED;

        // Idempotency check: if match is already in the target status with the same action
        if (match.getStatus() == targetStatus) {
            Optional<ReconciliationResolutionAudit> latest =
                    reconciliationResolutionAuditRepository.findTopByMatchIdOrderByResolvedAtDesc(matchId);
            if (latest.isPresent() && latest.get().getAction() == request.action()) {
                log.info("Discrepancy resolution idempotent hit for matchId={} action={}", matchId, request.action());
                return latest.get();
            }
        }

        // Action compatibility validation
        validateActionCompatibility(match.getStatus(), request.action());

        ReconciliationStatus previousStatus = match.getStatus();
        match.setStatus(targetStatus);
        reconciliationMatchRepository.save(match);

        ReconciliationResolutionAudit audit = new ReconciliationResolutionAudit(
                match,
                request.action(),
                request.resolvedBy(),
                request.notes(),
                previousStatus,
                targetStatus
        );

        ReconciliationResolutionAudit savedAudit = reconciliationResolutionAuditRepository.save(audit);
        log.info("Discrepancy resolved for matchId={} from {} to {} action={} by={}",
                matchId, previousStatus, targetStatus, request.action(), request.resolvedBy());

        return savedAudit;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationResolutionAudit> getResolutionHistory(Long matchId) {
        if (!reconciliationMatchRepository.existsById(matchId)) {
            throw new ReconciliationMatchNotFoundException(matchId);
        }
        return reconciliationResolutionAuditRepository.findByMatchIdOrderByResolvedAtDesc(matchId);
    }

    private void validateActionCompatibility(ReconciliationStatus currentStatus, ResolutionAction action) {
        if (currentStatus == ReconciliationStatus.MATCHED) {
            throw new InvalidResolutionActionException("Cannot resolve a match that is already in MATCHED status");
        }

        switch (action) {
            case APPROVE_FEE_ADJUSTMENT -> {
                if (currentStatus != ReconciliationStatus.FEE_DISCREPANCY) {
                    throw new InvalidResolutionActionException(
                            "APPROVE_FEE_ADJUSTMENT is only valid for FEE_DISCREPANCY, but current status is " + currentStatus);
                }
            }
            case ACCEPT_AMOUNT_VARIANCE -> {
                if (currentStatus != ReconciliationStatus.AMOUNT_MISMATCH) {
                    throw new InvalidResolutionActionException(
                            "ACCEPT_AMOUNT_VARIANCE is only valid for AMOUNT_MISMATCH, but current status is " + currentStatus);
                }
            }
            case DISMISS_ORPHAN -> {
                if (currentStatus != ReconciliationStatus.UNMATCHED_INTERNAL
                        && currentStatus != ReconciliationStatus.UNMATCHED_EXTERNAL) {
                    throw new InvalidResolutionActionException(
                            "DISMISS_ORPHAN is only valid for UNMATCHED_INTERNAL or UNMATCHED_EXTERNAL, but current status is " + currentStatus);
                }
            }
            case ESCALATE_DISPUTE, MANUAL_OVERRIDE_MATCH -> {
                // Valid for all discrepancy states
            }
        }
    }
}
