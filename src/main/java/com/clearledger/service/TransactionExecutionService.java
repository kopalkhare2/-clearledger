package com.clearledger.service;

import com.clearledger.domain.*;
import com.clearledger.dto.TransferRequest;
import com.clearledger.exception.*;
import com.clearledger.repository.AccountRepository;
import com.clearledger.repository.LedgerEntryRepository;
import com.clearledger.repository.TransactionRepository;
import com.clearledger.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal execution service responsible for executing the atomic financial transfer.
 * Annotated with @Transactional so that Spring proxies the transaction boundary correctly.
 */
@Service
public class TransactionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionExecutionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionExecutionService(AccountRepository accountRepository,
                                     TransactionRepository transactionRepository,
                                     LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Executes the atomic transfer in a single transaction:
     * 1. Acquires pessimistic write locks on accounts in ascending ID order to prevent deadlocks.
     * 2. Double-checks idempotency key after locks are acquired.
     * 3. Validates business constraints (active status, currency match, sufficient balance).
     * 4. Debits source and credits destination.
     * 5. Creates exactly 1 COMPLETED Transaction.
     * 6. Creates exactly 2 balanced LedgerEntry rows (1 DEBIT, 1 CREDIT).
     * 7. Verifies double-entry ledger invariants.
     * 8. Commits atomically.
     */
    @Transactional
    public Transaction executeTransfer(TransferRequest request) {
        long start = Instant.now().toEpochMilli();

        // Step 1: Preliminary validation
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }

        BigDecimal amount = Money.of(request.amount());
        if (!Money.isPositive(amount)) {
            throw new InvalidTransactionException("Transfer amount must be positive");
        }

        // Step 2: Acquire pessimistic write locks in ascending ID order to prevent deadlocks
        Long lowerId = Math.min(request.sourceAccountId(), request.destinationAccountId());
        Long higherId = Math.max(request.sourceAccountId(), request.destinationAccountId());

        Account first = accountRepository.findByIdWithLock(lowerId)
                .orElseThrow(() -> new AccountNotFoundException(lowerId));
        Account second = accountRepository.findByIdWithLock(higherId)
                .orElseThrow(() -> new AccountNotFoundException(higherId));

        Account source = first.getId().equals(request.sourceAccountId()) ? first : second;
        Account destination = first.getId().equals(request.destinationAccountId()) ? first : second;

        // Step 3: Double-checked idempotency lookup inside the locked critical section
        var existingInsideLock = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingInsideLock.isPresent()) {
            Transaction tx = existingInsideLock.get();
            verifyIdempotencyMatch(tx, request);
            log.info("Idempotency match (inside locked section): returning transaction reference={}",
                    tx.getTransactionReference());
            return tx;
        }

        // Step 4: Business validations on locked accounts
        if (!source.isActive()) {
            throw new AccountInactiveException(source.getId());
        }
        if (!destination.isActive()) {
            throw new AccountInactiveException(destination.getId());
        }
        if (!source.getCurrency().equals(request.currency())) {
            throw new CurrencyMismatchException(request.currency(), source.getCurrency());
        }
        if (!destination.getCurrency().equals(request.currency())) {
            throw new CurrencyMismatchException(request.currency(), destination.getCurrency());
        }
        if (!source.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(source.getId(), source.getBalance(), amount);
        }

        // Step 5: Mutate account balances
        source.debit(amount);
        destination.credit(amount);
        accountRepository.save(source);
        accountRepository.save(destination);

        // Step 6: Create and save Transaction (immediately COMPLETED)
        String reference = UUID.randomUUID().toString();
        Transaction tx = new Transaction(reference, source, destination, amount, request.currency(), request.idempotencyKey());
        tx.markCompleted();
        Transaction savedTx = transactionRepository.save(tx);

        // Step 7: Persist balanced Ledger entries
        LedgerEntry debitEntry = new LedgerEntry(savedTx, source, EntryType.DEBIT, amount);
        LedgerEntry creditEntry = new LedgerEntry(savedTx, destination, EntryType.CREDIT, amount);
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        ledgerEntryRepository.flush();

        // Step 8: Verify double-entry ledger invariant
        verifyLedgerInvariant(savedTx.getId());

        long elapsed = Instant.now().toEpochMilli() - start;
        log.info("Transfer completed: reference={} amount={} {} source={} dest={} durationMs={}",
                savedTx.getTransactionReference(), amount, request.currency(),
                source.getAccountNumber(), destination.getAccountNumber(), elapsed);

        return savedTx;
    }

    private void verifyIdempotencyMatch(Transaction existing, TransferRequest request) {
        boolean sourceMatch = existing.getSourceAccount().getId().equals(request.sourceAccountId());
        boolean destMatch = existing.getDestinationAccount().getId().equals(request.destinationAccountId());
        boolean amountMatch = Money.equals(existing.getAmount(), request.amount());
        boolean currMatch = existing.getCurrency().equals(request.currency());

        if (!sourceMatch || !destMatch || !amountMatch || !currMatch) {
            throw new IdempotencyConflictException(request.idempotencyKey());
        }
    }

    private void verifyLedgerInvariant(Long transactionId) {
        long entryCount = ledgerEntryRepository.countByTransactionId(transactionId);
        if (entryCount != 2) {
            throw new LedgerInvariantViolationException(
                    String.format("Expected 2 ledger entries for transaction %d, found %d",
                            transactionId, entryCount));
        }

        BigDecimal totalDebits = ledgerEntryRepository.sumAmountByTransactionIdAndEntryType(
                transactionId, EntryType.DEBIT);
        BigDecimal totalCredits = ledgerEntryRepository.sumAmountByTransactionIdAndEntryType(
                transactionId, EntryType.CREDIT);

        if (!Money.equals(totalDebits, totalCredits)) {
            throw new LedgerInvariantViolationException(
                    String.format("Debit total (%s) != Credit total (%s) for transaction %d",
                            totalDebits, totalCredits, transactionId));
        }
    }
}
