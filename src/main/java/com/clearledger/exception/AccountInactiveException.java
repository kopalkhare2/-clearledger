package com.clearledger.exception;

public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException(Long accountId) {
        super("Account is inactive and cannot process transactions: " + accountId);
    }
}
