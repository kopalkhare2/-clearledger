package com.clearledger.service;

import com.clearledger.domain.Account;
import com.clearledger.domain.EntryType;
import com.clearledger.domain.LedgerEntry;
import com.clearledger.dto.AccountStatementItemDto;
import com.clearledger.dto.AccountStatementResponse;
import com.clearledger.dto.TrialBalanceReportDto;
import com.clearledger.exception.AccountNotFoundException;
import com.clearledger.exception.InvalidDateRangeException;
import com.clearledger.mapper.AuditMapper;
import com.clearledger.repository.AccountRepository;
import com.clearledger.repository.LedgerEntryRepository;
import com.clearledger.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrialBalanceAuditService {

    private static final LocalDateTime EPOCH_START = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final AuditMapper auditMapper;

    public TrialBalanceAuditService(LedgerEntryRepository ledgerEntryRepository,
                                   AccountRepository accountRepository,
                                   AuditMapper auditMapper) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.auditMapper = auditMapper;
    }

    /**
     * Generates a system-wide trial balance audit report over an optional date window.
     * Verifies the core double-entry invariant: SUM(DEBIT) == SUM(CREDIT).
     */
    @Transactional(readOnly = true)
    public TrialBalanceReportDto generateTrialBalance(LocalDateTime from, LocalDateTime to) {
        validateDateRange(from, to);

        LocalDateTime normalizedFrom = from != null ? from : EPOCH_START;
        LocalDateTime normalizedTo = to != null ? to : LocalDateTime.now();

        BigDecimal totalDebits = ledgerEntryRepository.sumAmountByEntryTypeAndCreatedAtBetween(
                EntryType.DEBIT, normalizedFrom, normalizedTo);
        BigDecimal totalCredits = ledgerEntryRepository.sumAmountByEntryTypeAndCreatedAtBetween(
                EntryType.CREDIT, normalizedFrom, normalizedTo);
        long entryCount = ledgerEntryRepository.countByCreatedAtBetween(normalizedFrom, normalizedTo);
        long accountCount = accountRepository.count();

        boolean isBalanced = Money.equals(totalDebits, totalCredits);

        return new TrialBalanceReportDto(
                totalDebits,
                totalCredits,
                isBalanced,
                entryCount,
                accountCount,
                from,
                to,
                LocalDateTime.now()
        );
    }

    /**
     * Generates a point-in-time account statement with reconstructed opening balance,
     * chronological running balances, and closing balance verification.
     */
    @Transactional(readOnly = true)
    public AccountStatementResponse generateAccountStatement(Long accountId, LocalDateTime from, LocalDateTime to) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        validateDateRange(from, to);

        LocalDateTime normalizedFrom = from != null ? from : EPOCH_START;
        LocalDateTime normalizedTo = to != null ? to : LocalDateTime.now();

        // 1. Reconstruct historical opening balance immediately prior to normalizedFrom
        BigDecimal creditsBefore = ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAndCreatedAtBefore(
                accountId, EntryType.CREDIT, normalizedFrom);
        BigDecimal debitsBefore = ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAndCreatedAtBefore(
                accountId, EntryType.DEBIT, normalizedFrom);
        BigDecimal openingBalance = creditsBefore.subtract(debitsBefore);

        // 2. Fetch entries within window [normalizedFrom, normalizedTo] in chronological order
        List<LedgerEntry> entriesInWindow = ledgerEntryRepository
                .findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(accountId, normalizedFrom, normalizedTo);

        // 3. Compute running balance line by line
        BigDecimal runningBalance = openingBalance;
        BigDecimal totalDebited = BigDecimal.ZERO;
        BigDecimal totalCredited = BigDecimal.ZERO;
        List<AccountStatementItemDto> statementItems = new ArrayList<>();

        for (LedgerEntry entry : entriesInWindow) {
            if (entry.getEntryType() == EntryType.CREDIT) {
                runningBalance = runningBalance.add(entry.getAmount());
                totalCredited = totalCredited.add(entry.getAmount());
            } else {
                runningBalance = runningBalance.subtract(entry.getAmount());
                totalDebited = totalDebited.add(entry.getAmount());
            }
            statementItems.add(auditMapper.toStatementItem(entry, runningBalance));
        }

        // 4. Closing balance calculation
        BigDecimal closingBalance = openingBalance.add(totalCredited).subtract(totalDebited);

        // 5. Verify against persisted balance if window reaches present time
        boolean isClosingBalanceVerified = (to == null || !to.isBefore(LocalDateTime.now().minusSeconds(10)))
                ? Money.equals(closingBalance, account.getBalance())
                : true;

        return new AccountStatementResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                openingBalance,
                closingBalance,
                totalDebited,
                totalCredited,
                from,
                to,
                isClosingBalanceVerified,
                statementItems
        );
    }

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(
                    String.format("Invalid date range: 'from' (%s) cannot be after 'to' (%s)", from, to));
        }
    }
}
