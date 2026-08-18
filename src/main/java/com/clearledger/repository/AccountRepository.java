package com.clearledger.repository;

import com.clearledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Loads an account with a PostgreSQL SELECT FOR UPDATE lock.
     * Used exclusively during balance mutation in TransactionService.
     * Prevents concurrent transfers from corrupting the balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    /**
     * Loads an account with its User association eagerly via JOIN FETCH.
     * Required for read-only queries where open-in-view=false would otherwise
     * cause a LazyInitializationException when the mapper accesses account.getUser().
     */
    @Query("SELECT a FROM Account a JOIN FETCH a.user WHERE a.id = :id")
    Optional<Account> findByIdWithUser(@Param("id") Long id);
}
