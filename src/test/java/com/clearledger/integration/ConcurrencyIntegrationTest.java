package com.clearledger.integration;

import com.clearledger.domain.Account;
import com.clearledger.domain.Transaction;
import com.clearledger.domain.TransactionStatus;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.dto.TransferRequest;
import com.clearledger.repository.*;
import com.clearledger.service.AccountService;
import com.clearledger.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Concurrency integration tests verifying:
 *   1. Concurrent spending: balance never goes negative under parallel transfers, debits == credits.
 *   2. Concurrent identical idempotency: exactly one transaction created for 10 parallel duplicates.
 *   3. Rollback integrity: failed transfers leave zero partial state, and subsequent retries succeed cleanly.
 */
class ConcurrencyIntegrationTest extends BaseIntegrationTest {

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
                new CreateAccountRequest("Alice", "alice_c@example.com", "INR"));
        destination = accountService.createAccount(
                new CreateAccountRequest("Bob", "bob_c@example.com", "INR"));

        // Fund source with ₹10,000
        transactionTemplate.execute(status -> {
            Account s = accountRepository.findById(source.getId()).orElseThrow();
            s.credit(new BigDecimal("10000.00"));
            accountRepository.save(s);
            return null;
        });
    }

    /**
     * Test: 4 concurrent transfer attempts (₹7000, ₹6000, ₹4000, ₹3000)
     * from a ₹10,000 account.
     *
     * Invariants proven:
     *   - Final source balance ≥ 0 (never negative)
     *   - Total successful debits ≤ initial balance (₹10,000)
     *   - Total successful debits == Total destination credits
     *   - Exactly 2 ledger entries per successful transaction
     *   - Zero orphaned or partial records
     */
    @Test
    void concurrentSpending_balanceNeverGoesNegative() throws InterruptedException {
        int threads = 4;
        BigDecimal[] amounts = {
            new BigDecimal("7000.00"),
            new BigDecimal("6000.00"),
            new BigDecimal("4000.00"),
            new BigDecimal("3000.00")
        };

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        List<BigDecimal> successAmounts = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final BigDecimal amount = amounts[i];
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    transactionService.transfer(new TransferRequest(
                            source.getId(), destination.getId(),
                            amount, "INR", UUID.randomUUID().toString()));
                    successCount.incrementAndGet();
                    successAmounts.add(amount);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify balance invariants
        Account finalSource = accountRepository.findById(source.getId()).orElseThrow();
        Account finalDest   = accountRepository.findById(destination.getId()).orElseThrow();
        BigDecimal finalSourceBalance = finalSource.getBalance();
        BigDecimal finalDestBalance   = finalDest.getBalance();

        assertThat(finalSourceBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        BigDecimal totalDebited = successAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebited).isLessThanOrEqualTo(new BigDecimal("10000.00"));

        BigDecimal expectedSourceBalance = new BigDecimal("10000.00").subtract(totalDebited);
        assertThat(finalSourceBalance).isEqualByComparingTo(expectedSourceBalance);
        assertThat(finalDestBalance).isEqualByComparingTo(totalDebited);

        // Ledger entries count = exactly 2 per successful transaction
        long entryCount = ledgerEntryRepository.count();
        assertThat(entryCount).isEqualTo((long) successCount.get() * 2);

        // All created transactions are COMPLETED
        long completedTxCount = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .count();
        assertThat(completedTxCount).isEqualTo(successCount.get());
    }

    /**
     * Test: 10 concurrent requests with the EXACT SAME idempotency key and payload.
     *
     * Invariants proven:
     *   - All 10 concurrent callers succeed
     *   - All 10 callers observe the SAME transaction reference
     *   - Returned transaction status is COMPLETED for all callers (never PENDING)
     *   - Exactly 1 transaction row exists in database
     *   - Exactly 2 ledger entries exist in database
     *   - Source balance debited exactly once (₹10,000 -> ₹9,000)
     *   - Destination balance credited exactly once (₹0 -> ₹1,000)
     */
    @Test
    void concurrentIdenticalIdempotencyKey_exactlyOneTransaction() throws InterruptedException {
        int threads = 10;
        String sharedKey = UUID.randomUUID().toString();
        TransferRequest req = new TransferRequest(
                source.getId(), destination.getId(),
                new BigDecimal("1000.00"), "INR", sharedKey);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Transaction> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    Transaction tx = transactionService.transfer(req);
                    results.add(tx);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // 1. All callers succeeded
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(results).hasSize(threads);

        // 2. All received the same transaction reference and ID
        String expectedRef = results.get(0).getTransactionReference();
        Long expectedId    = results.get(0).getId();
        assertThat(results).allMatch(tx -> tx.getTransactionReference().equals(expectedRef));
        assertThat(results).allMatch(tx -> tx.getId().equals(expectedId));

        // 3. All observed status = COMPLETED (no caller saw PENDING)
        assertThat(results).allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED);

        // 4. Exactly 1 transaction in the DB
        assertThat(transactionRepository.countByIdempotencyKey(sharedKey)).isEqualTo(1);

        // 5. Exactly 2 ledger entries in the DB
        Transaction dbTx = transactionRepository.findByIdempotencyKey(sharedKey).orElseThrow();
        assertThat(ledgerEntryRepository.countByTransactionId(dbTx.getId())).isEqualTo(2);

        // 6. Balances changed exactly once
        Account finalSource = accountRepository.findById(source.getId()).orElseThrow();
        Account finalDest   = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(finalSource.getBalance()).isEqualByComparingTo("9000.00");
        assertThat(finalDest.getBalance()).isEqualByComparingTo("1000.00");
    }

    /**
     * Rollback integrity test:
     * When a transfer fails (insufficient balance):
     *   - Source and destination balances remain completely untouched
     *   - No partial ledger entries exist
     *   - No transaction row exists
     */
    @Test
    void failedTransfer_leavesNoPartialState() {
        long entryCountBefore = ledgerEntryRepository.count();
        long txCountBefore = transactionRepository.count();
        Account srcBefore = accountRepository.findById(source.getId()).orElseThrow();
        Account dstBefore = accountRepository.findById(destination.getId()).orElseThrow();

        // Attempt a transfer that will fail (over balance)
        try {
            transactionService.transfer(new TransferRequest(
                    source.getId(), destination.getId(),
                    new BigDecimal("99999.00"), "INR", UUID.randomUUID().toString()));
        } catch (Exception expected) {
            // expected
        }

        // Balances completely unchanged
        Account srcAfter = accountRepository.findById(source.getId()).orElseThrow();
        Account dstAfter = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcBefore.getBalance());
        assertThat(dstAfter.getBalance()).isEqualByComparingTo(dstBefore.getBalance());

        // Zero partial ledger entries created
        assertThat(ledgerEntryRepository.count()).isEqualTo(entryCountBefore);

        // Zero transactions created
        assertThat(transactionRepository.count()).isEqualTo(txCountBefore);
    }

    /**
     * Rollback & Retry test:
     * Verifies that after a failed transfer (rolled back), retrying with the SAME
     * idempotency key after funding the account succeeds cleanly, producing
     * exactly one transaction and two ledger entries.
     */
    @Test
    void failedTransfer_thenFundAccount_thenRetrySameKeySucceeds() {
        String sharedKey = UUID.randomUUID().toString();
        TransferRequest req = new TransferRequest(
                source.getId(), destination.getId(),
                new BigDecimal("15000.00"), "INR", sharedKey);

        // Step 1: Attempt transfer with insufficient balance (₹10,000 < ₹15,000) -> fails
        assertThatThrownBy(() -> transactionService.transfer(req));

        // Invariant: zero transaction records persisted
        assertThat(transactionRepository.findByIdempotencyKey(sharedKey)).isEmpty();
        assertThat(ledgerEntryRepository.count()).isEqualTo(0);

        // Step 2: Fund source account with additional ₹10,000 (total = ₹20,000)
        transactionTemplate.execute(status -> {
            Account s = accountRepository.findById(source.getId()).orElseThrow();
            s.credit(new BigDecimal("10000.00"));
            accountRepository.save(s);
            return null;
        });

        // Step 3: Retry with the EXACT SAME idempotency key -> now succeeds
        Transaction tx = transactionService.transfer(req);

        assertThat(tx).isNotNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionRepository.countByIdempotencyKey(sharedKey)).isEqualTo(1);
        assertThat(ledgerEntryRepository.countByTransactionId(tx.getId())).isEqualTo(2);

        Account finalSource = accountRepository.findById(source.getId()).orElseThrow();
        Account finalDest   = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(finalSource.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(finalDest.getBalance()).isEqualByComparingTo("15000.00");
    }
}
