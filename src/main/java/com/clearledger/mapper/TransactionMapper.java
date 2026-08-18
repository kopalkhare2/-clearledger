package com.clearledger.mapper;

import com.clearledger.domain.Transaction;
import com.clearledger.dto.TransactionResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionReference(),
                tx.getSourceAccount().getId(),
                tx.getSourceAccount().getAccountNumber(),
                tx.getDestinationAccount().getId(),
                tx.getDestinationAccount().getAccountNumber(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getIdempotencyKey(),
                tx.getCreatedAt(),
                tx.getCompletedAt());
    }
}
