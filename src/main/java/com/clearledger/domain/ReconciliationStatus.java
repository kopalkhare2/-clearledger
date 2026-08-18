package com.clearledger.domain;

public enum ReconciliationStatus {
    MATCHED,
    AMOUNT_MISMATCH,
    FEE_DISCREPANCY,
    UNMATCHED_INTERNAL,
    UNMATCHED_EXTERNAL,
    RESOLVED,
    DISPUTED
}
