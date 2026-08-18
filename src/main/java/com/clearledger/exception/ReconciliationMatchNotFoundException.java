package com.clearledger.exception;

public class ReconciliationMatchNotFoundException extends RuntimeException {
    public ReconciliationMatchNotFoundException(Long matchId) {
        super("Reconciliation match not found: " + matchId);
    }
}
