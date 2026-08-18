package com.clearledger.integration;

import com.clearledger.domain.*;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.dto.TransferRequest;
import com.clearledger.exception.*;
import com.clearledger.repository.*;
import com.clearledger.service.AccountService;
import com.clearledger.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TransactionIntegrationTest extends BaseIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired TransactionService transactionService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private Account source;
    private Account destination;

    @BeforeEach
    void setup() {
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        source = accountService.createAccount(
                new CreateAccountRequest("Alice", "alice@example.com", "INR"));
        destination = accountService.createAccount(
                new CreateAccountRequest("Bob", "bob@example.com", "INR"));

        // Fund source account
        transactionTemplate.execute(status -> {
            Account s = accountRepository.findById(source.getId()).orElseThrow();
            s.credit(new BigDecimal("10000.00"));
            accountRepository.save(s);
            return null;
        });
    }

    @Test
    void transfer_succeeds_updatesBalancesAndLedger() {
        var req = transfer("1000.00", key());
        Transaction tx = transactionService.transfer(req);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getTransactionReference()).isNotBlank();

        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        Account updatedDest   = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualByComparingTo("9000.00");
        assertThat(updatedDest.getBalance()).isEqualByComparingTo("1000.00");

        // Verify double-entry ledger
        var entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries).anyMatch(e -> e.getEntryType() == EntryType.DEBIT);
        assertThat(entries).anyMatch(e -> e.getEntryType() == EntryType.CREDIT);
        entries.forEach(e -> assertThat(e.getAmount()).isEqualByComparingTo("1000.00"));
    }

    @Test
    void transfer_rejectsInsufficientBalance() {
        var req = transfer("99999.00", key());
        assertThatThrownBy(() -> transactionService.transfer(req))
                .isInstanceOf(InsufficientBalanceException.class);

        // Balance unchanged
        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    void transfer_rejectsSameSourceDestination() {
        var req = new TransferRequest(source.getId(), source.getId(),
                new BigDecimal("100.00"), "INR", key());
        assertThatThrownBy(() -> transactionService.transfer(req))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void transfer_rejectsMissingSource() {
        var req = new TransferRequest(99999L, destination.getId(),
                new BigDecimal("100.00"), "INR", key());
        assertThatThrownBy(() -> transactionService.transfer(req))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transfer_rejectsMissingDestination() {
        var req = new TransferRequest(source.getId(), 99999L,
                new BigDecimal("100.00"), "INR", key());
        assertThatThrownBy(() -> transactionService.transfer(req))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transfer_rejectsCurrencyMismatch() {
        // Create a USD destination
        Account usdDest = accountService.createAccount(
                new CreateAccountRequest("Carol", "carol@example.com", "USD"));
        var req = new TransferRequest(source.getId(), usdDest.getId(),
                new BigDecimal("100.00"), "INR", key());
        assertThatThrownBy(() -> transactionService.transfer(req))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void transfer_rejectsInactiveAccount() {
        transactionTemplate.execute(status -> {
            Account s = accountRepository.findById(source.getId()).orElseThrow();
            s.setStatus(AccountStatus.INACTIVE);
            accountRepository.save(s);
            return null;
        });
        var req = transfer("100.00", key());
        assertThatThrownBy(() -> transactionService.transfer(req))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void transfer_idempotency_returnsSameResultOnRepeat() {
        String idempotencyKey = key();
        var req = transfer("500.00", idempotencyKey);

        Transaction first  = transactionService.transfer(req);
        Transaction second = transactionService.transfer(req);

        assertThat(second.getTransactionReference()).isEqualTo(first.getTransactionReference());
        assertThat(second.getId()).isEqualTo(first.getId());

        // Exactly one transaction row
        assertThat(transactionRepository.countByIdempotencyKey(idempotencyKey)).isEqualTo(1);

        // Exactly 2 ledger entries
        assertThat(ledgerEntryRepository.countByTransactionId(first.getId())).isEqualTo(2);

        // Balance changed only once
        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualByComparingTo("9500.00");
    }

    @Test
    void transfer_idempotency_rejectsConflictingPayload() {
        String idempotencyKey = key();
        transactionService.transfer(transfer("500.00", idempotencyKey));

        // Same key, different amount → conflict
        var conflicting = new TransferRequest(source.getId(), destination.getId(),
                new BigDecimal("999.00"), "INR", idempotencyKey);
        assertThatThrownBy(() -> transactionService.transfer(conflicting))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TransferRequest transfer(String amount, String idempotencyKey) {
        return new TransferRequest(source.getId(), destination.getId(),
                new BigDecimal(amount), "INR", idempotencyKey);
    }

    private String key() {
        return UUID.randomUUID().toString();
    }
}
