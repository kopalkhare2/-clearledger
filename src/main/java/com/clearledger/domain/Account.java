package com.clearledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a financial account belonging to a User.
 * A user may hold multiple accounts (different currencies).
 *
 * IMPORTANT: balance is stored as BigDecimal (NUMERIC in PostgreSQL).
 * Never use float/double for monetary values.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Monetary balance stored with scale=2 (cents precision).
     * The DB CHECK constraint enforces balance >= 0.
     * Service-layer pessimistic locking prevents concurrent negative-balance race conditions.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Constructors ─────────────────────────────────────────
    protected Account() {}

    public Account(User user, String accountNumber, String currency) {
        this.user = user;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }

    // ── Domain methods ────────────────────────────────────────
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient balance for debit");
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters ───────────────────────────────────────────────
    public Long getId()                          { return id; }
    public User getUser()                        { return user; }
    public String getAccountNumber()             { return accountNumber; }
    public String getCurrency()                  { return currency; }
    public BigDecimal getBalance()               { return balance; }
    public AccountStatus getStatus()             { return status; }
    public void setStatus(AccountStatus status)  { this.status = status; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
}
