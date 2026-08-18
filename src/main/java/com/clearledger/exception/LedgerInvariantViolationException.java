package com.clearledger.exception;

/**
 * Thrown when the double-entry ledger invariant (SUM debits == SUM credits) fails.
 * This is a critical financial integrity error — the entire transaction is rolled back.
 */
public class LedgerInvariantViolationException extends RuntimeException {
    public LedgerInvariantViolationException(String message) {
        super("CRITICAL — Ledger invariant violated: " + message);
    }
}
