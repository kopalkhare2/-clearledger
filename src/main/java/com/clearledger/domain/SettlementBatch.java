package com.clearledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "settlement_batches")
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_reference", nullable = false, unique = true, length = 64)
    private String batchReference;

    @Column(name = "source_provider", nullable = false, length = 64)
    private String sourceProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementBatchStatus status = SettlementBatchStatus.PENDING;

    @Column(name = "total_records", nullable = false)
    private Integer totalRecords = 0;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SettlementRecord> records = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public SettlementBatch() {}

    public SettlementBatch(String batchReference, String sourceProvider) {
        this.batchReference = batchReference;
        this.sourceProvider = sourceProvider;
        this.status = SettlementBatchStatus.PENDING;
    }

    public void addRecord(SettlementRecord record) {
        records.add(record);
        record.setBatch(this);
    }

    public void markProcessing() {
        this.status = SettlementBatchStatus.PROCESSING;
    }

    public void markReconciled() {
        this.status = SettlementBatchStatus.RECONCILED;
        this.reconciledAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = SettlementBatchStatus.FAILED;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getBatchReference() { return batchReference; }
    public void setBatchReference(String batchReference) { this.batchReference = batchReference; }
    public String getSourceProvider() { return sourceProvider; }
    public void setSourceProvider(String sourceProvider) { this.sourceProvider = sourceProvider; }
    public SettlementBatchStatus getStatus() { return status; }
    public void setStatus(SettlementBatchStatus status) { this.status = status; }
    public Integer getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public List<SettlementRecord> getRecords() { return records; }
    public void setRecords(List<SettlementRecord> records) { this.records = records; }
}
