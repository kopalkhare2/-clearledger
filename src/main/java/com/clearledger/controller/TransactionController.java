package com.clearledger.controller;

import com.clearledger.dto.TransactionResponse;
import com.clearledger.dto.TransferRequest;
import com.clearledger.mapper.TransactionMapper;
import com.clearledger.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Financial transfer endpoints")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping
    @Operation(
        summary = "Execute a transfer",
        description = """
            Transfers funds from one account to another using double-entry accounting.
            Idempotent: submitting the same idempotencyKey twice returns the original result.
            """
    )
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        var transaction = transactionService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionMapper.toResponse(transaction));
    }
}
