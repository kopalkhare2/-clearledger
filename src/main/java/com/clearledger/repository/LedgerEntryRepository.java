package com.clearledger.repository;

import com.clearledger.domain.EntryType;
import com.clearledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(Long transactionId);

    long countByTransactionId(Long transactionId);

    /**
     * Returns the sum of amounts for a given transaction and entry type.
     * Used to verify the ledger invariant: SUM(DEBIT) == SUM(CREDIT).
     */
    @Query("""
        SELECT COALESCE(SUM(le.amount), 0)
        FROM LedgerEntry le
        WHERE le.transaction.id = :transactionId
          AND le.entryType = :entryType
        """)
    BigDecimal sumAmountByTransactionIdAndEntryType(
            @Param("transactionId") Long transactionId,
            @Param("entryType") EntryType entryType);

    /**
     * Reconstructs historical balance before a given timestamp:
     * sum of amounts for an account and entry type before the given timestamp.
     */
    @Query("""
        SELECT COALESCE(SUM(le.amount), 0)
        FROM LedgerEntry le
        WHERE le.account.id = :accountId
          AND le.entryType = :entryType
          AND le.createdAt < :before
        """)
    BigDecimal sumAmountByAccountIdAndEntryTypeAndCreatedAtBefore(
            @Param("accountId") Long accountId,
            @Param("entryType") EntryType entryType,
            @Param("before") LocalDateTime before);

    /**
     * Retrieves account ledger entries in chronological order within a date window.
     */
    @Query("""
        SELECT le FROM LedgerEntry le
        WHERE le.account.id = :accountId
          AND le.createdAt >= :from
          AND le.createdAt <= :to
        ORDER BY le.createdAt ASC, le.id ASC
        """)
    List<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
            @Param("accountId") Long accountId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Returns total sum of amounts for an entry type across all accounts within a date window.
     */
    @Query("""
        SELECT COALESCE(SUM(le.amount), 0)
        FROM LedgerEntry le
        WHERE le.entryType = :entryType
          AND le.createdAt >= :from
          AND le.createdAt <= :to
        """)
    BigDecimal sumAmountByEntryTypeAndCreatedAtBetween(
            @Param("entryType") EntryType entryType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Returns lifetime total sum of amounts for an entry type across all accounts.
     */
    @Query("""
        SELECT COALESCE(SUM(le.amount), 0)
        FROM LedgerEntry le
        WHERE le.entryType = :entryType
        """)
    BigDecimal sumAmountByEntryType(@Param("entryType") EntryType entryType);

    /**
     * Returns total count of ledger entries within a date window.
     */
    @Query("""
        SELECT COUNT(le)
        FROM LedgerEntry le
        WHERE le.createdAt >= :from
          AND le.createdAt <= :to
        """)
    long countByCreatedAtBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
