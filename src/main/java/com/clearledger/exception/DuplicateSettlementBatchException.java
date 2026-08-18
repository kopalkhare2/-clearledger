package com.clearledger.exception;

public class DuplicateSettlementBatchException extends RuntimeException {
    public DuplicateSettlementBatchException(String batchReference) {
        super("Settlement batch already exists with reference: " + batchReference);
    }
}
