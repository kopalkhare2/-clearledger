package com.clearledger.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key conflict: a different request was previously submitted with key=" + idempotencyKey);
    }
}
