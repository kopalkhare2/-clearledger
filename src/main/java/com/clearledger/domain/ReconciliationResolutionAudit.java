package com.clearledger.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_resolution_history")
public class ReconciliationResolutionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private ReconciliationMatch match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResolutionAction action;

    @Column(name = "resolved_by", nullable = false, length = 64)
    private String resolvedBy;

    @Column(nullable = false, length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private ReconciliationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private ReconciliationStatus newStatus;

    @Column(name = "resolved_at", nullable = false, updatable = false)
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (resolvedAt == null) {
            resolvedAt = LocalDateTime.now();
        }
    }

    public ReconciliationResolutionAudit() {}

    public ReconciliationResolutionAudit(ReconciliationMatch match,
                                         ResolutionAction action,
                                         String resolvedBy,
                                         String notes,
                                         ReconciliationStatus previousStatus,
                                         ReconciliationStatus newStatus) {
        this.match = match;
        this.action = action;
        this.resolvedBy = resolvedBy;
        this.notes = notes;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.resolvedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public ReconciliationMatch getMatch() { return match; }
    public ResolutionAction getAction() { return action; }
    public String getResolvedBy() { return resolvedBy; }
    public String getNotes() { return notes; }
    public ReconciliationStatus getPreviousStatus() { return previousStatus; }
    public ReconciliationStatus getNewStatus() { return newStatus; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
