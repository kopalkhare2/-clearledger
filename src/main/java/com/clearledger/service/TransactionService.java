package com.clearledger.service;

import com.clearledger.domain.Transaction;
import com.clearledger.dto.TransferRequest;
import com.clearledger.exception.IdempotencyConflictException;
import com.clearledger.repository.TransactionRepository;
import com.clearledger.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public financial transaction service implementing:
 *   - Fast-path idempotency checks
 *   - Delegation to TransactionExecutionService for atomic transactional processing
 *   - Outer unique-key race condition recovery outside the rolled-back transaction boundary
 *   - Transaction history queries
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionExecutionService transactionExecutionService;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionExecutionService transactionExecutionService) {
        this.transactionRepository = transactionRepository;
        this.transactionExecutionService = transactionExecutionService;
    }

    /**
     * Executes a financial transfer with database-backed idempotency.
     *
     * Idempotency handling:
     *   1. Quick check: If a COMPLETED transaction with this idempotencyKey already exists,
     *      verify payload and return it immediately (fast-path for duplicate sequential calls).
     *   2. Execute transfer inside an atomic @Transactional boundary with pessimistic locking
     *      via TransactionExecutionService.
     *   3. If a concurrent race occurs at the database unique constraint level (DataIntegrityViolationException),
     *      the inner transaction rolls back cleanly, and this outer method catches it,
     *      reloads the committed winning transaction in a clean state, and returns it.
     */
    public Transaction transfer(TransferRequest request) {
        // Fast-path check before acquiring locks
        var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            verifyIdempotencyMatch(tx, request);
            log.info("Idempotency match (fast-path): returning completed transaction reference={}",
                    tx.getTransactionReference());
            return tx;
        }

        try {
            return transactionExecutionService.executeTransfer(request);
        } catch (DataIntegrityViolationException e) {
            // Concurrent unique-constraint race: another request committed with the same idempotency key.
            // Because transfer() is outside the rolled-back transaction boundary, we can safely query the DB.
            log.info("Idempotency unique constraint race detected for key={}. Reloading committed transaction.",
                    request.idempotencyKey());
            return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(tx -> {
                        verifyIdempotencyMatch(tx, request);
                        return tx;
                    })
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getTransactionHistory(Long accountId, Pageable pageable) {
        return transactionRepository.findByAccountId(accountId, pageable);
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
}
