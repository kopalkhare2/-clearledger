package com.clearledger.unit;

import com.clearledger.domain.*;
import com.clearledger.dto.ResolveDiscrepancyRequest;
import com.clearledger.exception.InvalidResolutionActionException;
import com.clearledger.exception.ReconciliationMatchNotFoundException;
import com.clearledger.repository.ReconciliationMatchRepository;
import com.clearledger.repository.ReconciliationResolutionAuditRepository;
import com.clearledger.service.DiscrepancyResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscrepancyResolutionTest {

    @Mock ReconciliationMatchRepository reconciliationMatchRepository;
    @Mock ReconciliationResolutionAuditRepository reconciliationResolutionAuditRepository;

    @InjectMocks DiscrepancyResolutionService discrepancyResolutionService;

    private SettlementBatch batch;
    private SettlementRecord record;

    @BeforeEach
    void setUp() {
        batch = new SettlementBatch("BATCH-UNIT-01", "STRIPE");
        record = new SettlementRecord("ext_1", "tx_1", new BigDecimal("1000.00"), new BigDecimal("25.00"),
                new BigDecimal("975.00"), "INR", LocalDateTime.now());
        lenient().when(reconciliationResolutionAuditRepository.save(any(ReconciliationResolutionAudit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void resolveDiscrepancy_approveFeeAdjustment_transitionsToResolvedAndSavesAudit() {
        ReconciliationMatch match = new ReconciliationMatch(
                batch, record, null, "tx_1", "ext_1",
                ReconciliationStatus.FEE_DISCREPANCY, MatchType.FEE_ADJUSTED,
                "Fee deducted", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                new BigDecimal("25.00"), new BigDecimal("975.00")
        );
        when(reconciliationMatchRepository.findById(1L)).thenReturn(Optional.of(match));

        var req = new ResolveDiscrepancyRequest(
                ResolutionAction.APPROVE_FEE_ADJUSTMENT, "auditor-1", "Fee approved per contract");

        ReconciliationResolutionAudit audit = discrepancyResolutionService.resolveDiscrepancy(1L, req);

        assertThat(match.getStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
        assertThat(audit.getAction()).isEqualTo(ResolutionAction.APPROVE_FEE_ADJUSTMENT);
        assertThat(audit.getPreviousStatus()).isEqualTo(ReconciliationStatus.FEE_DISCREPANCY);
        assertThat(audit.getNewStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
        assertThat(audit.getResolvedBy()).isEqualTo("auditor-1");
        assertThat(audit.getNotes()).isEqualTo("Fee approved per contract");
        verify(reconciliationResolutionAuditRepository, times(1)).save(any(ReconciliationResolutionAudit.class));
    }

    @Test
    void resolveDiscrepancy_escalateDispute_transitionsToDisputed() {
        ReconciliationMatch match = new ReconciliationMatch(
                batch, record, null, "tx_2", "ext_2",
                ReconciliationStatus.AMOUNT_MISMATCH, MatchType.NONE,
                "Amount mismatch", new BigDecimal("1000.00"), new BigDecimal("900.00"),
                BigDecimal.ZERO, new BigDecimal("900.00")
        );
        when(reconciliationMatchRepository.findById(2L)).thenReturn(Optional.of(match));

        var req = new ResolveDiscrepancyRequest(
                ResolutionAction.ESCALATE_DISPUTE, "auditor-2", "Disputing amount with bank");

        ReconciliationResolutionAudit audit = discrepancyResolutionService.resolveDiscrepancy(2L, req);

        assertThat(match.getStatus()).isEqualTo(ReconciliationStatus.DISPUTED);
        assertThat(audit.getPreviousStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
        assertThat(audit.getNewStatus()).isEqualTo(ReconciliationStatus.DISPUTED);
    }

    @Test
    void resolveDiscrepancy_idempotentRepeat_returnsExistingWithoutDuplicateSave() {
        ReconciliationMatch match = new ReconciliationMatch(
                batch, record, null, "tx_1", "ext_1",
                ReconciliationStatus.RESOLVED, MatchType.FEE_ADJUSTED,
                "Fee deducted", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                new BigDecimal("25.00"), new BigDecimal("975.00")
        );
        when(reconciliationMatchRepository.findById(1L)).thenReturn(Optional.of(match));

        ReconciliationResolutionAudit existingAudit = new ReconciliationResolutionAudit(
                match, ResolutionAction.APPROVE_FEE_ADJUSTMENT, "auditor-1", "Fee approved",
                ReconciliationStatus.FEE_DISCREPANCY, ReconciliationStatus.RESOLVED
        );
        when(reconciliationResolutionAuditRepository.findTopByMatchIdOrderByResolvedAtDesc(1L))
                .thenReturn(Optional.of(existingAudit));

        var req = new ResolveDiscrepancyRequest(
                ResolutionAction.APPROVE_FEE_ADJUSTMENT, "auditor-1", "Fee approved");

        ReconciliationResolutionAudit result = discrepancyResolutionService.resolveDiscrepancy(1L, req);

        assertThat(result).isSameAs(existingAudit);
        verify(reconciliationResolutionAuditRepository, never()).save(any(ReconciliationResolutionAudit.class));
    }

    @Test
    void resolveDiscrepancy_incompatibleAction_throwsInvalidResolutionAction() {
        ReconciliationMatch match = new ReconciliationMatch(
                batch, record, null, "tx_1", "ext_1",
                ReconciliationStatus.AMOUNT_MISMATCH, MatchType.NONE,
                "Amount mismatch", new BigDecimal("1000.00"), new BigDecimal("900.00"),
                BigDecimal.ZERO, new BigDecimal("900.00")
        );
        when(reconciliationMatchRepository.findById(1L)).thenReturn(Optional.of(match));

        var req = new ResolveDiscrepancyRequest(
                ResolutionAction.APPROVE_FEE_ADJUSTMENT, "auditor-1", "Invalid action on amount mismatch");

        assertThatThrownBy(() -> discrepancyResolutionService.resolveDiscrepancy(1L, req))
                .isInstanceOf(InvalidResolutionActionException.class)
                .hasMessageContaining("APPROVE_FEE_ADJUSTMENT is only valid for FEE_DISCREPANCY");
    }

    @Test
    void resolveDiscrepancy_matchNotFound_throwsException() {
        when(reconciliationMatchRepository.findById(999L)).thenReturn(Optional.empty());

        var req = new ResolveDiscrepancyRequest(
                ResolutionAction.ESCALATE_DISPUTE, "auditor-1", "Notes");

        assertThatThrownBy(() -> discrepancyResolutionService.resolveDiscrepancy(999L, req))
                .isInstanceOf(ReconciliationMatchNotFoundException.class);
    }
}
