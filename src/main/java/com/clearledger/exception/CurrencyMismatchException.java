package com.clearledger.exception;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String requestCurrency, String accountCurrency) {
        super(String.format(
            "Currency mismatch: request currency '%s' does not match account currency '%s'",
            requestCurrency, accountCurrency));
    }
}
