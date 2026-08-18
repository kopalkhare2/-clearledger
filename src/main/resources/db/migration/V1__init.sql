-- ============================================================
-- ClearLedger Phase 1 — Initial Schema
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- USERS
-- Stores identity; a user may hold multiple accounts.
-- No password_hash in Phase 1 — authentication deferred.
-- ──────────────────────────────────────────────────────────
CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Index: fast user lookup by email (login, deduplication)
CREATE INDEX idx_users_email ON users (email);

-- ──────────────────────────────────────────────────────────
-- ACCOUNTS
-- One user may have multiple accounts (different currencies).
-- Balance stored as NUMERIC(19,2) — never float/double.
-- CHECK constraint enforces non-negative balance at DB level.
-- ──────────────────────────────────────────────────────────
CREATE TABLE accounts (
    id             BIGSERIAL       PRIMARY KEY,
    user_id        BIGINT          NOT NULL,
    account_number VARCHAR(20)     NOT NULL,
    currency       VARCHAR(3)      NOT NULL,
    balance        NUMERIC(19, 2)  NOT NULL DEFAULT 0.00,
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_accounts_user  FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Indexes
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
CREATE INDEX idx_accounts_number  ON accounts (account_number);

-- ──────────────────────────────────────────────────────────
-- TRANSACTIONS
-- Each row represents a single transfer attempt.
-- idempotency_key is a DB-enforced unique constraint:
--   the database — not the application — is the final arbiter
--   of uniqueness, protecting against concurrent race conditions.
-- ──────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id                      BIGSERIAL       PRIMARY KEY,
    transaction_reference   VARCHAR(36)     NOT NULL,
    source_account_id       BIGINT          NOT NULL,
    destination_account_id  BIGINT          NOT NULL,
    amount                  NUMERIC(19, 2)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    idempotency_key         VARCHAR(255),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,

    CONSTRAINT uq_transactions_reference     UNIQUE (transaction_reference),
    CONSTRAINT uq_transactions_idempotency   UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_source        FOREIGN KEY (source_account_id)
                                             REFERENCES accounts (id),
    CONSTRAINT fk_transactions_destination   FOREIGN KEY (destination_account_id)
                                             REFERENCES accounts (id),
    CONSTRAINT chk_transactions_amount       CHECK (amount > 0),
    CONSTRAINT chk_transactions_status       CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

-- Indexes
-- transaction_reference: retrieval by public reference
CREATE INDEX idx_transactions_reference      ON transactions (transaction_reference);
-- idempotency_key: fast deduplication lookup
CREATE INDEX idx_transactions_idempotency    ON transactions (idempotency_key);
-- account lookup for history queries
CREATE INDEX idx_transactions_source         ON transactions (source_account_id);
CREATE INDEX idx_transactions_destination    ON transactions (destination_account_id);
-- time-ordered history (DESC)
CREATE INDEX idx_transactions_created_at     ON transactions (created_at DESC);

-- ──────────────────────────────────────────────────────────
-- LEDGER_ENTRIES
-- Implements double-entry accounting.
-- Every COMPLETED transaction produces exactly:
--   1 DEBIT  entry (source account)
--   1 CREDIT entry (destination account)
-- Invariant: SUM(DEBIT) == SUM(CREDIT) per transaction.
-- Amounts are always positive; direction is given by entry_type.
-- ──────────────────────────────────────────────────────────
CREATE TABLE ledger_entries (
    id             BIGSERIAL       PRIMARY KEY,
    transaction_id BIGINT          NOT NULL,
    account_id     BIGINT          NOT NULL,
    entry_type     VARCHAR(6)      NOT NULL,
    amount         NUMERIC(19, 2)  NOT NULL,
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ledger_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_ledger_account     FOREIGN KEY (account_id)     REFERENCES accounts (id),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount     CHECK (amount > 0)
);

-- Indexes
-- Lookup all entries for a transaction (invariant check, audit)
CREATE INDEX idx_ledger_transaction_id ON ledger_entries (transaction_id);
-- Lookup all entries for an account (account ledger view)
CREATE INDEX idx_ledger_account_id     ON ledger_entries (account_id);
