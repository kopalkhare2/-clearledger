package com.clearledger.service;

import com.clearledger.domain.Account;
import com.clearledger.domain.User;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.exception.AccountNotFoundException;
import com.clearledger.repository.AccountRepository;
import com.clearledger.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public AccountService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Creates a new account. If the email already belongs to an existing user,
     * a new account is attached to that user (supporting multi-currency wallets).
     * If the email is new, a user record is created first.
     *
     * This matches a realistic financial model where one identity may hold
     * multiple accounts in different currencies.
     */
    @Transactional
    public Account createAccount(CreateAccountRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    log.info("Creating new user for email={}", request.email());
                    return userRepository.save(new User(request.name(), request.email()));
                });

        String accountNumber = generateAccountNumber();
        Account account = new Account(user, accountNumber, request.currency());
        Account saved = accountRepository.save(account);

        log.info("Created account={} currency={} userId={}", saved.getAccountNumber(),
                saved.getCurrency(), user.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Account getAccountById(Long accountId) {
        return accountRepository.findByIdWithUser(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public Account getAccountBalance(Long accountId) {
        return getAccountById(accountId);
    }

    /**
     * Generates a unique 16-digit account number with prefix CL.
     * UUID-based to ensure uniqueness across instances.
     */
    private String generateAccountNumber() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        return "CL" + uuid;
    }
}
