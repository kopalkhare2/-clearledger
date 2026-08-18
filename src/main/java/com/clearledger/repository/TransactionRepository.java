package com.clearledger.repository;

import com.clearledger.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByTransactionReference(String reference);

    /**
     * Fetches paginated transaction history for an account (as source or destination).
     * Uses index idx_transactions_created_at for efficient ordering.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.sourceAccount.id = :accountId
           OR t.destinationAccount.id = :accountId
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    long countByIdempotencyKey(String idempotencyKey);
}
