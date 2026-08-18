package com.clearledger.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a ClearLedger user who may own one or more Accounts.
 * Authentication is out of scope for Phase 1; no password field is stored.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Account> accounts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Constructors ────────────────────────────────────────
    protected User() {}

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    // ── Getters & Setters ────────────────────────────────────
    public Long getId()                        { return id; }
    public String getName()                    { return name; }
    public void   setName(String name)         { this.name = name; }
    public String getEmail()                   { return email; }
    public UserStatus getStatus()              { return status; }
    public void   setStatus(UserStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public List<Account> getAccounts()         { return accounts; }
}
