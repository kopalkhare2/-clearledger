-- ============================================================
-- ClearLedger Phase 2 — Financial Reconciliation Schema
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- SETTLEMENT_BATCHES
-- Tracks external batch settlement ingestion (e.g. daily bank/processor feeds)
-- ──────────────────────────────────────────────────────────
CREATE TABLE settlement_batches (
    id              BIGSERIAL       PRIMARY KEY,
    batch_reference VARCHAR(64)     NOT NULL,
    source_provider VARCHAR(64)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    total_records   INT             NOT NULL DEFAULT 0,
    total_amount    NUMERIC(19, 2)  NOT NULL DEFAULT 0.00,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    reconciled_at   TIMESTAMP,

    CONSTRAINT uq_settlement_batches_reference UNIQUE (batch_reference),
    CONSTRAINT chk_settlement_batches_status CHECK (status IN ('PENDING', 'PROCESSING', 'RECONCILED', 'FAILED'))
);

CREATE INDEX idx_settlement_batches_reference ON settlement_batches (batch_reference);
CREATE INDEX idx_settlement_batches_status    ON settlement_batches (status);

-- ──────────────────────────────────────────────────────────
-- SETTLEMENT_RECORDS
-- Individual line items in an external settlement batch
-- ──────────────────────────────────────────────────────────
CREATE TABLE settlement_records (
    id                      BIGSERIAL       PRIMARY KEY,
    batch_id                BIGINT          NOT NULL,
    external_tx_id          VARCHAR(128)    NOT NULL,
    internal_tx_reference   VARCHAR(36),
    gross_amount            NUMERIC(19, 2)  NOT NULL,
    fee                     NUMERIC(19, 2)  NOT NULL DEFAULT 0.00,
    net_amount              NUMERIC(19, 2)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL,
    settlement_date         TIMESTAMP       NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'UNRECONCILED',
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_settlement_records_batch FOREIGN KEY (batch_id) REFERENCES settlement_batches (id) ON DELETE CASCADE,
    CONSTRAINT uq_settlement_records_batch_ext UNIQUE (batch_id, external_tx_id),
    CONSTRAINT chk_settlement_records_gross_positive CHECK (gross_amount > 0),
    CONSTRAINT chk_settlement_records_fee_non_negative CHECK (fee >= 0),
    CONSTRAINT chk_settlement_records_status CHECK (status IN ('UNRECONCILED', 'MATCHED', 'DISCREPANCY', 'UNMATCHED'))
);

CREATE INDEX idx_settlement_records_batch_id     ON settlement_records (batch_id);
CREATE INDEX idx_settlement_records_internal_ref ON settlement_records (internal_tx_reference);
CREATE INDEX idx_settlement_records_external_id  ON settlement_records (external_tx_id);

-- ──────────────────────────────────────────────────────────
-- RECONCILIATION_MATCHES
-- Tracks deterministic matching results and discrepancy audit trail
-- Non-destructive: original Phase 1 transactions/ledger entries are immutable.
-- ──────────────────────────────────────────────────────────
CREATE TABLE reconciliation_matches (
    id                      BIGSERIAL       PRIMARY KEY,
    batch_id                BIGINT          NOT NULL,
    settlement_record_id    BIGINT,
    internal_transaction_id BIGINT,
    internal_tx_reference   VARCHAR(36),
    external_tx_id          VARCHAR(128),
    status                  VARCHAR(30)     NOT NULL,
    match_type              VARCHAR(20)     NOT NULL,
    discrepancy_reason      VARCHAR(255),
    internal_amount         NUMERIC(19, 2),
    external_gross_amount   NUMERIC(19, 2),
    external_fee            NUMERIC(19, 2),
    external_net_amount     NUMERIC(19, 2),
    reconciled_at           TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_reconcil_batch FOREIGN KEY (batch_id) REFERENCES settlement_batches (id) ON DELETE CASCADE,
    CONSTRAINT fk_reconcil_settlement_rec FOREIGN KEY (settlement_record_id) REFERENCES settlement_records (id) ON DELETE SET NULL,
    CONSTRAINT fk_reconcil_internal_tx FOREIGN KEY (internal_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    CONSTRAINT uq_reconcil_batch_internal_tx UNIQUE (batch_id, internal_transaction_id),
    CONSTRAINT uq_reconcil_batch_settlement_rec UNIQUE (batch_id, settlement_record_id),
    CONSTRAINT chk_reconcil_status CHECK (status IN ('MATCHED', 'AMOUNT_MISMATCH', 'FEE_DISCREPANCY', 'UNMATCHED_INTERNAL', 'UNMATCHED_EXTERNAL')),
    CONSTRAINT chk_reconcil_match_type CHECK (match_type IN ('EXACT', 'FEE_ADJUSTED', 'MANUAL', 'NONE'))
);

CREATE INDEX idx_reconcil_batch_id         ON reconciliation_matches (batch_id);
CREATE INDEX idx_reconcil_status           ON reconciliation_matches (status);
CREATE INDEX idx_reconcil_internal_tx_id   ON reconciliation_matches (internal_transaction_id);
CREATE INDEX idx_reconcil_settlement_rec_id ON reconciliation_matches (settlement_record_id);
