package com.clearledger.mapper;

import com.clearledger.domain.ReconciliationMatch;
import com.clearledger.domain.ReconciliationStatus;
import com.clearledger.domain.SettlementBatch;
import com.clearledger.dto.ReconciliationMatchDto;
import com.clearledger.dto.ReconciliationSummaryResponse;
import com.clearledger.dto.SettlementBatchResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ReconciliationMapper {

    public SettlementBatchResponse toBatchResponse(SettlementBatch batch) {
        return new SettlementBatchResponse(
                batch.getId(),
                batch.getBatchReference(),
                batch.getSourceProvider(),
                batch.getStatus(),
                batch.getTotalRecords(),
                batch.getTotalAmount(),
                batch.getCreatedAt(),
                batch.getReconciledAt()
        );
    }

    public ReconciliationMatchDto toMatchDto(ReconciliationMatch match) {
        return new ReconciliationMatchDto(
                match.getId(),
                match.getBatch().getId(),
                match.getSettlementRecord() != null ? match.getSettlementRecord().getId() : null,
                match.getInternalTransaction() != null ? match.getInternalTransaction().getId() : null,
                match.getInternalTxReference(),
                match.getExternalTxId(),
                match.getStatus(),
                match.getMatchType(),
                match.getDiscrepancyReason(),
                match.getInternalAmount(),
                match.getExternalGrossAmount(),
                match.getExternalFee(),
                match.getExternalNetAmount(),
                match.getReconciledAt()
        );
    }

    public ReconciliationSummaryResponse toSummaryResponse(SettlementBatch batch, List<ReconciliationMatch> matches) {
        int matched = 0;
        int amountMismatch = 0;
        int feeDiscrepancy = 0;
        int unmatchedInternal = 0;
        int unmatchedExternal = 0;

        BigDecimal totalInternal = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (ReconciliationMatch m : matches) {
            if (m.getStatus() == ReconciliationStatus.MATCHED) matched++;
            else if (m.getStatus() == ReconciliationStatus.AMOUNT_MISMATCH) amountMismatch++;
            else if (m.getStatus() == ReconciliationStatus.FEE_DISCREPANCY) feeDiscrepancy++;
            else if (m.getStatus() == ReconciliationStatus.UNMATCHED_INTERNAL) unmatchedInternal++;
            else if (m.getStatus() == ReconciliationStatus.UNMATCHED_EXTERNAL) unmatchedExternal++;

            if (m.getInternalAmount() != null) {
                totalInternal = totalInternal.add(m.getInternalAmount());
            }
            if (m.getExternalGrossAmount() != null) {
                totalGross = totalGross.add(m.getExternalGrossAmount());
            }
            if (m.getExternalFee() != null) {
                totalFees = totalFees.add(m.getExternalFee());
            }
            if (m.getExternalNetAmount() != null) {
                totalNet = totalNet.add(m.getExternalNetAmount());
            }
        }

        List<ReconciliationMatchDto> dtos = matches.stream().map(this::toMatchDto).toList();

        return new ReconciliationSummaryResponse(
                batch.getId(),
                batch.getBatchReference(),
                batch.getStatus(),
                matches.size(),
                matched,
                amountMismatch,
                feeDiscrepancy,
                unmatchedInternal,
                unmatchedExternal,
                totalInternal,
                totalGross,
                totalFees,
                totalNet,
                batch.getReconciledAt(),
                dtos
        );
    }
}
