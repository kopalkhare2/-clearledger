package com.clearledger.controller;

import com.clearledger.dto.AccountResponse;
import com.clearledger.dto.BalanceResponse;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.dto.TransactionResponse;
import com.clearledger.mapper.AccountMapper;
import com.clearledger.mapper.TransactionMapper;
import com.clearledger.service.AccountService;
import com.clearledger.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account management endpoints")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AccountMapper accountMapper;
    private final TransactionMapper transactionMapper;

    public AccountController(AccountService accountService, TransactionService transactionService,
                              AccountMapper accountMapper, TransactionMapper transactionMapper) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping
    @Operation(summary = "Create a new account",
               description = "Creates a user (or finds existing by email) and opens a new account")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        var account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(account));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("accountId") Long accountId) {
        var account = accountService.getAccountById(accountId);
        return ResponseEntity.ok(accountMapper.toResponse(account));
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get current balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable("accountId") Long accountId) {
        var account = accountService.getAccountBalance(accountId);
        return ResponseEntity.ok(accountMapper.toBalanceResponse(account));
    }

    @GetMapping("/{accountId}/transactions")
    @Operation(summary = "Get paginated transaction history for an account")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable("accountId") Long accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<TransactionResponse> page = transactionService
                .getTransactionHistory(accountId, pageable)
                .map(transactionMapper::toResponse);
        return ResponseEntity.ok(page);
    }
}
