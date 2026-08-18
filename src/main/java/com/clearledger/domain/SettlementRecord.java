package com.clearledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_records")
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementBatch batch;

    @Column(name = "external_tx_id", nullable = false, length = 128)
    private String externalTxId;

    @Column(name = "internal_tx_reference", length = 36)
    private String internalTxReference;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "settlement_date", nullable = false)
    private LocalDateTime settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementRecordStatus status = SettlementRecordStatus.UNRECONCILED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public SettlementRecord() {}

    public SettlementRecord(String externalTxId, String internalTxReference,
                            BigDecimal grossAmount, BigDecimal fee, BigDecimal netAmount,
                            String currency, LocalDateTime settlementDate) {
        this.externalTxId = externalTxId;
        this.internalTxReference = internalTxReference;
        this.grossAmount = grossAmount;
        this.fee = fee != null ? fee : BigDecimal.ZERO;
        this.netAmount = netAmount;
        this.currency = currency;
        this.settlementDate = settlementDate;
        this.status = SettlementRecordStatus.UNRECONCILED;
    }

    public void markMatched() {
        this.status = SettlementRecordStatus.MATCHED;
    }

    public void markDiscrepancy() {
        this.status = SettlementRecordStatus.DISCREPANCY;
    }

    public void markUnmatched() {
        this.status = SettlementRecordStatus.UNMATCHED;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public SettlementBatch getBatch() { return batch; }
    public void setBatch(SettlementBatch batch) { this.batch = batch; }
    public String getExternalTxId() { return externalTxId; }
    public void setExternalTxId(String externalTxId) { this.externalTxId = externalTxId; }
    public String getInternalTxReference() { return internalTxReference; }
    public void setInternalTxReference(String internalTxReference) { this.internalTxReference = internalTxReference; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDateTime settlementDate) { this.settlementDate = settlementDate; }
    public SettlementRecordStatus getStatus() { return status; }
    public void setStatus(SettlementRecordStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
