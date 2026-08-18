package com.clearledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ClearLedger — Phase 1 Financial Ledger Monolith
 *
 * Demonstrates: double-entry accounting, ACID transactions, pessimistic locking,
 * idempotency, concurrency safety, and clean layered architecture.
 */
@SpringBootApplication
public class ClearLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClearLedgerApplication.class, args);
    }
}
