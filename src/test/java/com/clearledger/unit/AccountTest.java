package com.clearledger.unit;

import com.clearledger.domain.Account;
import com.clearledger.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        User user = new User("Test User", "test@example.com");
        account = new Account(user, "CL001", "INR");
    }

    @Test
    void initialBalanceIsZero() {
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void credit_increasesBalance() {
        account.credit(new BigDecimal("500.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void debit_decreasesBalance() {
        account.credit(new BigDecimal("1000.00"));
        account.debit(new BigDecimal("300.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void debit_throwsWhenInsufficientBalance() {
        account.credit(new BigDecimal("100.00"));
        assertThatThrownBy(() -> account.debit(new BigDecimal("200.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debit_throwsWhenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasSufficientBalance_returnsTrueForExact() {
        account.credit(new BigDecimal("100.00"));
        assertThat(account.hasSufficientBalance(new BigDecimal("100.00"))).isTrue();
    }

    @Test
    void hasSufficientBalance_returnsFalseWhenShort() {
        account.credit(new BigDecimal("50.00"));
        assertThat(account.hasSufficientBalance(new BigDecimal("100.00"))).isFalse();
    }
}
