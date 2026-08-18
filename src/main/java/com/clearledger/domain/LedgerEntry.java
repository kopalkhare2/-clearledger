package com.clearledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single line in the double-entry ledger.
 *
 * Each completed transaction produces exactly:
 *   1 DEBIT  entry (source account balance decreases)
 *   1 CREDIT entry (destination account balance increases)
 *
 * Amounts are always positive. Direction is encoded by entry_type.
 * Ledger invariant: SUM(DEBIT amounts) == SUM(CREDIT amounts) per transaction.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ── Constructors ─────────────────────────────────────────
    protected LedgerEntry() {}

    public LedgerEntry(Transaction transaction, Account account,
                       EntryType entryType, BigDecimal amount) {
        this.transaction = transaction;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
    }

    // ── Getters ───────────────────────────────────────────────
    public Long getId()                  { return id; }
    public Transaction getTransaction()  { return transaction; }
    public Account getAccount()          { return account; }
    public EntryType getEntryType()      { return entryType; }
    public BigDecimal getAmount()        { return amount; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
}
