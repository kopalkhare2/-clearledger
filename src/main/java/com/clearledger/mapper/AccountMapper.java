package com.clearledger.mapper;

import com.clearledger.domain.Account;
import com.clearledger.dto.AccountResponse;
import com.clearledger.dto.BalanceResponse;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                new AccountResponse.UserSummary(
                        account.getUser().getId(),
                        account.getUser().getName(),
                        account.getUser().getEmail()),
                account.getCreatedAt());
    }

    public BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance());
    }
}
