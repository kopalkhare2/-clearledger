package com.clearledger.exception;

public class SettlementBatchNotFoundException extends RuntimeException {
    public SettlementBatchNotFoundException(Long batchId) {
        super("Settlement batch not found: " + batchId);
    }
    public SettlementBatchNotFoundException(String batchReference) {
        super("Settlement batch not found: " + batchReference);
    }
}
