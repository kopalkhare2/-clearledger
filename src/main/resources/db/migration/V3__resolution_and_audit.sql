-- ============================================================
-- ClearLedger Phase 3 — Resolution Workflow & Audit Trail Schema
-- ============================================================

-- 1. Expand reconciliation_matches status check constraint to include RESOLVED and DISPUTED
ALTER TABLE reconciliation_matches DROP CONSTRAINT chk_reconcil_status;
ALTER TABLE reconciliation_matches ADD CONSTRAINT chk_reconcil_status
    CHECK (status IN (
        'MATCHED',
        'AMOUNT_MISMATCH',
        'FEE_DISCREPANCY',
        'UNMATCHED_INTERNAL',
        'UNMATCHED_EXTERNAL',
        'RESOLVED',
        'DISPUTED'
    ));

-- 2. Permanent, append-only reconciliation resolution history
-- Uses ON DELETE RESTRICT to guarantee audit records cannot be accidentally deleted
CREATE TABLE reconciliation_resolution_history (
    id                  BIGSERIAL       PRIMARY KEY,
    match_id            BIGINT          NOT NULL,
    action              VARCHAR(32)     NOT NULL,
    resolved_by         VARCHAR(64)     NOT NULL,
    notes               VARCHAR(500)    NOT NULL,
    previous_status     VARCHAR(30)     NOT NULL,
    new_status          VARCHAR(30)     NOT NULL,
    resolved_at         TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_resolution_match FOREIGN KEY (match_id) REFERENCES reconciliation_matches (id) ON DELETE RESTRICT,
    CONSTRAINT chk_resolution_action CHECK (action IN (
        'APPROVE_FEE_ADJUSTMENT',
        'ACCEPT_AMOUNT_VARIANCE',
        'DISMISS_ORPHAN',
        'ESCALATE_DISPUTE',
        'MANUAL_OVERRIDE_MATCH'
    ))
);

CREATE INDEX idx_resolution_match_id    ON reconciliation_resolution_history (match_id);
CREATE INDEX idx_resolution_resolved_at ON reconciliation_resolution_history (resolved_at);
