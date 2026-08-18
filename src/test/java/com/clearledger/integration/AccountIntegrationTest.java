package com.clearledger.integration;

import com.clearledger.domain.Account;
import com.clearledger.dto.CreateAccountRequest;
import com.clearledger.exception.AccountNotFoundException;
import com.clearledger.repository.AccountRepository;
import com.clearledger.repository.UserRepository;
import com.clearledger.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.*;

class AccountIntegrationTest extends BaseIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired com.clearledger.repository.TransactionRepository transactionRepository;
    @Autowired com.clearledger.repository.LedgerEntryRepository ledgerEntryRepository;

    @BeforeEach
    void cleanup() {
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createAccount_createsUserAndAccount() {
        var req = new CreateAccountRequest("Kopal Khare", "kopal@example.com", "INR");
        Account account = accountService.createAccount(req);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getAccountNumber()).startsWith("CL");
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getUser().getEmail()).isEqualTo("kopal@example.com");
    }

    @Test
    void createAccount_reuseExistingUser_forSameEmail() {
        var req1 = new CreateAccountRequest("Kopal Khare", "kopal@example.com", "INR");
        var req2 = new CreateAccountRequest("Kopal Khare", "kopal@example.com", "USD");

        accountService.createAccount(req1);
        accountService.createAccount(req2);

        // Same user, two different accounts
        long userCount = userRepository.count();
        long accountCount = accountRepository.count();
        assertThat(userCount).isEqualTo(1);
        assertThat(accountCount).isEqualTo(2);
    }

    @Test
    void getAccountById_returnsAccount() {
        var req = new CreateAccountRequest("Test User", "tu@example.com", "INR");
        Account created = accountService.createAccount(req);

        Account fetched = accountService.getAccountById(created.getId());
        assertThat(fetched.getAccountNumber()).isEqualTo(created.getAccountNumber());
    }

    @Test
    void getAccountById_throwsWhenNotFound() {
        assertThatThrownBy(() -> accountService.getAccountById(99999L))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
