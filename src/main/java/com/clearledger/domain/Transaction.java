package com.clearledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a transfer between two accounts.
 *
 * idempotency_key has a DB-unique constraint: the database enforces
 * uniqueness even under concurrent concurrent requests.
 *
 * A transaction begins PENDING and moves to COMPLETED or FAILED.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, unique = true)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private Account destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ── Constructors ─────────────────────────────────────────
    protected Transaction() {}

    public Transaction(String transactionReference, Account sourceAccount,
                       Account destinationAccount, BigDecimal amount,
                       String currency, String idempotencyKey) {
        this.transactionReference = transactionReference;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = TransactionStatus.PENDING;
    }

    // ── Domain methods ────────────────────────────────────────
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    // ── Getters ───────────────────────────────────────────────
    public Long getId()                              { return id; }
    public String getTransactionReference()          { return transactionReference; }
    public Account getSourceAccount()                { return sourceAccount; }
    public Account getDestinationAccount()           { return destinationAccount; }
    public BigDecimal getAmount()                    { return amount; }
    public String getCurrency()                      { return currency; }
    public TransactionStatus getStatus()             { return status; }
    public String getIdempotencyKey()                { return idempotencyKey; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public LocalDateTime getCompletedAt()            { return completedAt; }
    public List<LedgerEntry> getLedgerEntries()      { return ledgerEntries; }
}
