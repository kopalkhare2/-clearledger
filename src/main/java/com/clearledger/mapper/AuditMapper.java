package com.clearledger.mapper;

import com.clearledger.domain.LedgerEntry;
import com.clearledger.domain.ReconciliationResolutionAudit;
import com.clearledger.dto.AccountStatementItemDto;
import com.clearledger.dto.ResolutionAuditResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AuditMapper {

    public ResolutionAuditResponse toResolutionResponse(ReconciliationResolutionAudit audit) {
        return new ResolutionAuditResponse(
                audit.getId(),
                audit.getMatch().getId(),
                audit.getAction(),
                audit.getResolvedBy(),
                audit.getNotes(),
                audit.getPreviousStatus(),
                audit.getNewStatus(),
                audit.getResolvedAt()
        );
    }

    public List<ResolutionAuditResponse> toResolutionResponses(List<ReconciliationResolutionAudit> audits) {
        return audits.stream().map(this::toResolutionResponse).toList();
    }

    public AccountStatementItemDto toStatementItem(LedgerEntry entry, BigDecimal runningBalance) {
        String txRef = entry.getTransaction() != null ? entry.getTransaction().getTransactionReference() : null;
        return new AccountStatementItemDto(
                entry.getId(),
                txRef,
                entry.getEntryType(),
                entry.getAmount(),
                runningBalance,
                entry.getCreatedAt()
        );
    }
}
