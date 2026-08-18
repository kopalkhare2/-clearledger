package com.clearledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_matches")
public class ReconciliationMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_record_id")
    private SettlementRecord settlementRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "internal_transaction_id")
    private Transaction internalTransaction;

    @Column(name = "internal_tx_reference", length = 36)
    private String internalTxReference;

    @Column(name = "external_tx_id", length = 128)
    private String externalTxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReconciliationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType;

    @Column(name = "discrepancy_reason")
    private String discrepancyReason;

    @Column(name = "internal_amount", precision = 19, scale = 2)
    private BigDecimal internalAmount;

    @Column(name = "external_gross_amount", precision = 19, scale = 2)
    private BigDecimal externalGrossAmount;

    @Column(name = "external_fee", precision = 19, scale = 2)
    private BigDecimal externalFee;

    @Column(name = "external_net_amount", precision = 19, scale = 2)
    private BigDecimal externalNetAmount;

    @Column(name = "reconciled_at", nullable = false)
    private LocalDateTime reconciledAt;

    @PrePersist
    protected void onCreate() {
        if (reconciledAt == null) {
            reconciledAt = LocalDateTime.now();
        }
    }

    public ReconciliationMatch() {}

    public ReconciliationMatch(SettlementBatch batch,
                               SettlementRecord settlementRecord,
                               Transaction internalTransaction,
                               String internalTxReference,
                               String externalTxId,
                               ReconciliationStatus status,
                               MatchType matchType,
                               String discrepancyReason,
                               BigDecimal internalAmount,
                               BigDecimal externalGrossAmount,
                               BigDecimal externalFee,
                               BigDecimal externalNetAmount) {
        this.batch = batch;
        this.settlementRecord = settlementRecord;
        this.internalTransaction = internalTransaction;
        this.internalTxReference = internalTxReference;
        this.externalTxId = externalTxId;
        this.status = status;
        this.matchType = matchType;
        this.discrepancyReason = discrepancyReason;
        this.internalAmount = internalAmount;
        this.externalGrossAmount = externalGrossAmount;
        this.externalFee = externalFee;
        this.externalNetAmount = externalNetAmount;
        this.reconciledAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public SettlementBatch getBatch() { return batch; }
    public void setBatch(SettlementBatch batch) { this.batch = batch; }
    public SettlementRecord getSettlementRecord() { return settlementRecord; }
    public void setSettlementRecord(SettlementRecord settlementRecord) { this.settlementRecord = settlementRecord; }
    public Transaction getInternalTransaction() { return internalTransaction; }
    public void setInternalTransaction(Transaction internalTransaction) { this.internalTransaction = internalTransaction; }
    public String getInternalTxReference() { return internalTxReference; }
    public void setInternalTxReference(String internalTxReference) { this.internalTxReference = internalTxReference; }
    public String getExternalTxId() { return externalTxId; }
    public void setExternalTxId(String externalTxId) { this.externalTxId = externalTxId; }
    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }
    public MatchType getMatchType() { return matchType; }
    public void setMatchType(MatchType matchType) { this.matchType = matchType; }
    public String getDiscrepancyReason() { return discrepancyReason; }
    public void setDiscrepancyReason(String discrepancyReason) { this.discrepancyReason = discrepancyReason; }
    public BigDecimal getInternalAmount() { return internalAmount; }
    public void setInternalAmount(BigDecimal internalAmount) { this.internalAmount = internalAmount; }
    public BigDecimal getExternalGrossAmount() { return externalGrossAmount; }
    public void setExternalGrossAmount(BigDecimal externalGrossAmount) { this.externalGrossAmount = externalGrossAmount; }
    public BigDecimal getExternalFee() { return externalFee; }
    public void setExternalFee(BigDecimal externalFee) { this.externalFee = externalFee; }
    public BigDecimal getExternalNetAmount() { return externalNetAmount; }
    public void setExternalNetAmount(BigDecimal externalNetAmount) { this.externalNetAmount = externalNetAmount; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}
