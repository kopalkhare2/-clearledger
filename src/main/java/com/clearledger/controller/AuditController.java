package com.clearledger.controller;

import com.clearledger.dto.AccountStatementResponse;
import com.clearledger.dto.TrialBalanceReportDto;
import com.clearledger.service.TrialBalanceAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit & Trial Balance", description = "System-wide double-entry ledger audits and account statements")
public class AuditController {

    private final TrialBalanceAuditService trialBalanceAuditService;

    public AuditController(TrialBalanceAuditService trialBalanceAuditService) {
        this.trialBalanceAuditService = trialBalanceAuditService;
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Generate system trial balance", description = "Audits system-wide double-entry balance over optional date range")
    public ResponseEntity<TrialBalanceReportDto> getTrialBalance(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {
        TrialBalanceReportDto report = trialBalanceAuditService.generateTrialBalance(from, to);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/accounts/{id}/statement")
    @Operation(summary = "Generate account statement", description = "Reconstructs historical opening balance, chronological running balances, and closing balance")
    public ResponseEntity<AccountStatementResponse> getAccountStatement(
            @PathVariable("id") Long accountId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {
        AccountStatementResponse statement = trialBalanceAuditService.generateAccountStatement(accountId, from, to);
        return ResponseEntity.ok(statement);
    }
}
