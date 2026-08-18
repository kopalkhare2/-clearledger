package com.clearledger.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(Long accountId, BigDecimal available, BigDecimal required) {
        super(String.format(
            "Insufficient balance on account %d: available=%s, required=%s",
            accountId, available, required));
    }
}
