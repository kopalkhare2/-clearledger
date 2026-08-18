package com.clearledger.integration;

import com.clearledger.domain.Account;
import com.clearledger.dto.AccountStatementResponse;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.dto.TransferRequest;
import com.clearledger.dto.TrialBalanceReportDto;
import com.clearledger.repository.*;
import com.clearledger.service.AccountService;
import com.clearledger.service.TransactionService;
import com.clearledger.service.TrialBalanceAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrialBalanceAuditIntegrationTest extends BaseIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired TransactionService transactionService;
    @Autowired TrialBalanceAuditService trialBalanceAuditService;

    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired UserRepository userRepository;
    @Autowired SettlementBatchRepository settlementBatchRepository;
    @Autowired SettlementRecordRepository settlementRecordRepository;
    @Autowired ReconciliationMatchRepository reconciliationMatchRepository;
    @Autowired ReconciliationResolutionAuditRepository reconciliationResolutionAuditRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private Account accA;
    private Account accB;
    private Account accC;
    private Account accD;

    @BeforeEach
    void cleanup() {
        reconciliationResolutionAuditRepository.deleteAll();
        reconciliationMatchRepository.deleteAll();
        settlementRecordRepository.deleteAll();
        settlementBatchRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        accA = accountService.createAccount(new CreateAccountRequest("User A", "usera_tb@example.com", "INR"));
        accB = accountService.createAccount(new CreateAccountRequest("User B", "userb_tb@example.com", "INR"));
        accC = accountService.createAccount(new CreateAccountRequest("User C", "userc_tb@example.com", "INR"));
        accD = accountService.createAccount(new CreateAccountRequest("User D", "userd_tb@example.com", "INR"));

        // Fund accounts A & B directly
        transactionTemplate.execute(status -> {
            Account a = accountRepository.findById(accA.getId()).orElseThrow();
            Account b = accountRepository.findById(accB.getId()).orElseThrow();
            a.credit(new BigDecimal("10000.00"));
            b.credit(new BigDecimal("20000.00"));
            accountRepository.save(a);
            accountRepository.save(b);
            return null;
        });
    }

    @Test
    void trialBalanceAndAccountStatement_accuratelyAuditsLedgerAndReconstructsBalances() throws Exception {
        // 1. Execute multiple transfers across accounts
        // Transfer 1: A -> C (1,000 INR)
        transactionService.transfer(new TransferRequest(
                accA.getId(), accC.getId(), new BigDecimal("1000.00"), "INR", UUID.randomUUID().toString()));

        Thread.sleep(20);
        LocalDateTime t1 = LocalDateTime.now();
        Thread.sleep(20);

        // Transfer 2: B -> C (3,000 INR)
        transactionService.transfer(new TransferRequest(
                accB.getId(), accC.getId(), new BigDecimal("3000.00"), "INR", UUID.randomUUID().toString()));

        // Transfer 3: C -> D (500 INR)
        transactionService.transfer(new TransferRequest(
                accC.getId(), accD.getId(), new BigDecimal("500.00"), "INR", UUID.randomUUID().toString()));

        LocalDateTime t2 = LocalDateTime.now();

        // 2. Global Trial Balance Verification (all-time)
        TrialBalanceReportDto globalReport = trialBalanceAuditService.generateTrialBalance(null, null);
        assertThat(globalReport.isBalanced()).isTrue();
        assertThat(globalReport.totalDebits()).isEqualByComparingTo(globalReport.totalCredits());
        assertThat(globalReport.accountCount()).isEqualTo(4);
        assertThat(globalReport.entryCount()).isEqualTo(6); // 3 transfers * 2 entries each
        // Total transfers amount = 1000 + 3000 + 500 = 4500 (each has 1 DEBIT + 1 CREDIT = 4500 each)
        assertThat(globalReport.totalDebits()).isEqualByComparingTo("4500.00");
        assertThat(globalReport.totalCredits()).isEqualByComparingTo("4500.00");

        // 3. Complete Account Statement for Account C from epoch
        AccountStatementResponse statementFull = trialBalanceAuditService.generateAccountStatement(accC.getId(), null, null);
        assertThat(statementFull.openingBalance()).isEqualByComparingTo("0.00");
        assertThat(statementFull.totalCredited()).isEqualByComparingTo("4000.00"); // 1000 + 3000
        assertThat(statementFull.totalDebited()).isEqualByComparingTo("500.00");
        assertThat(statementFull.closingBalance()).isEqualByComparingTo("3500.00");
        assertThat(statementFull.isClosingBalanceVerified()).isTrue();
        assertThat(statementFull.entries()).hasSize(3);

        // Verify sequential running balances:
        // Entry 1 (+1000) -> running balance = 1000.00
        assertThat(statementFull.entries().get(0).runningBalance()).isEqualByComparingTo("1000.00");
        // Entry 2 (+3000) -> running balance = 4000.00
        assertThat(statementFull.entries().get(1).runningBalance()).isEqualByComparingTo("4000.00");
        // Entry 3 (-500)  -> running balance = 3500.00
        assertThat(statementFull.entries().get(2).runningBalance()).isEqualByComparingTo("3500.00");

        // 4. Bounded Window Account Statement for Account C starting after Transfer 1 (from = t1, to = t2)
        AccountStatementResponse statementWindow = trialBalanceAuditService.generateAccountStatement(accC.getId(), t1, t2);
        // Opening balance before t1 must be reconstructed as 1000.00
        assertThat(statementWindow.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(statementWindow.totalCredited()).isEqualByComparingTo("3000.00");
        assertThat(statementWindow.totalDebited()).isEqualByComparingTo("500.00");
        assertThat(statementWindow.closingBalance()).isEqualByComparingTo("3500.00");
        assertThat(statementWindow.entries()).hasSize(2);
        assertThat(statementWindow.entries().get(0).runningBalance()).isEqualByComparingTo("4000.00");
        assertThat(statementWindow.entries().get(1).runningBalance()).isEqualByComparingTo("3500.00");
        assertThat(statementWindow.isClosingBalanceVerified()).isTrue();
    }
}
