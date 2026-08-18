package com.clearledger.unit;

import com.clearledger.domain.Account;
import com.clearledger.domain.EntryType;
import com.clearledger.domain.LedgerEntry;
import com.clearledger.domain.User;
import com.clearledger.dto.AccountStatementResponse;
import com.clearledger.dto.TrialBalanceReportDto;
import com.clearledger.exception.InvalidDateRangeException;
import com.clearledger.mapper.AuditMapper;
import com.clearledger.repository.AccountRepository;
import com.clearledger.repository.LedgerEntryRepository;
import com.clearledger.service.TrialBalanceAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialBalanceAuditTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock AccountRepository accountRepository;
    private final AuditMapper auditMapper = new AuditMapper();

    private TrialBalanceAuditService trialBalanceAuditService;

    private Account account;

    @BeforeEach
    void setUp() {
        trialBalanceAuditService = new TrialBalanceAuditService(ledgerEntryRepository, accountRepository, auditMapper);
        User user = new User("Alice", "alice_audit@example.com");
        account = new Account(user, "CL_TEST_001", "INR");
        account.credit(new BigDecimal("5000.00"));
    }

    @Test
    void generateTrialBalance_balancedEntries_reportsBalancedTrue() {
        when(ledgerEntryRepository.sumAmountByEntryTypeAndCreatedAtBetween(eq(EntryType.DEBIT), any(), any()))
                .thenReturn(new BigDecimal("25000.00"));
        when(ledgerEntryRepository.sumAmountByEntryTypeAndCreatedAtBetween(eq(EntryType.CREDIT), any(), any()))
                .thenReturn(new BigDecimal("25000.00"));
        when(ledgerEntryRepository.countByCreatedAtBetween(any(), any())).thenReturn(50L);
        when(accountRepository.count()).thenReturn(10L);

        TrialBalanceReportDto report = trialBalanceAuditService.generateTrialBalance(null, null);

        assertThat(report.isBalanced()).isTrue();
        assertThat(report.totalDebits()).isEqualByComparingTo("25000.00");
        assertThat(report.totalCredits()).isEqualByComparingTo("25000.00");
        assertThat(report.entryCount()).isEqualTo(50L);
        assertThat(report.accountCount()).isEqualTo(10L);
    }

    @Test
    void generateTrialBalance_invalidDateRange_throwsInvalidDateRangeException() {
        LocalDateTime from = LocalDateTime.now();
        LocalDateTime to = from.minusDays(1);

        assertThatThrownBy(() -> trialBalanceAuditService.generateTrialBalance(from, to))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("cannot be after 'to'");
    }

    @Test
    void generateAccountStatement_reconstructsOpeningAndRunningBalancesCorrectly() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        LocalDateTime from = LocalDateTime.now().minusDays(5);
        LocalDateTime to = LocalDateTime.now();

        // 1. Opening balance before 'from': 2000 credit - 500 debit = 1500 opening
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAndCreatedAtBefore(1L, EntryType.CREDIT, from))
                .thenReturn(new BigDecimal("2000.00"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAndCreatedAtBefore(1L, EntryType.DEBIT, from))
                .thenReturn(new BigDecimal("500.00"));

        // 2. Window entries: +4000 credit, -500 debit -> closing = 1500 + 4000 - 500 = 5000 (matches account balance)
        LedgerEntry e1 = new LedgerEntry(null, account, EntryType.CREDIT, new BigDecimal("4000.00"));
        LedgerEntry e2 = new LedgerEntry(null, account, EntryType.DEBIT, new BigDecimal("500.00"));

        when(ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(eq(1L), eq(from), eq(to)))
                .thenReturn(List.of(e1, e2));

        AccountStatementResponse statement = trialBalanceAuditService.generateAccountStatement(1L, from, to);

        assertThat(statement.openingBalance()).isEqualByComparingTo("1500.00");
        assertThat(statement.closingBalance()).isEqualByComparingTo("5000.00");
        assertThat(statement.totalCredited()).isEqualByComparingTo("4000.00");
        assertThat(statement.totalDebited()).isEqualByComparingTo("5000.00".substring(0, 0) + "500.00");
        assertThat(statement.isClosingBalanceVerified()).isTrue();
        assertThat(statement.entries()).hasSize(2);
        assertThat(statement.entries().get(0).runningBalance()).isEqualByComparingTo("5500.00");
        assertThat(statement.entries().get(1).runningBalance()).isEqualByComparingTo("5000.00");
    }
}
