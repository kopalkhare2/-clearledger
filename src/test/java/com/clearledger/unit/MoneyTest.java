package com.clearledger.unit;

import com.clearledger.util.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void of_scalesTo2dp() {
        BigDecimal result = Money.of(new BigDecimal("10.555"));
        assertThat(result).isEqualByComparingTo("10.56"); // HALF_EVEN rounds .555 → .56
    }

    @Test
    void add_returnsCorrectSum() {
        assertThat(Money.add(new BigDecimal("1.10"), new BigDecimal("2.20")))
                .isEqualByComparingTo("3.30");
    }

    @Test
    void subtract_returnsCorrectDifference() {
        assertThat(Money.subtract(new BigDecimal("10.00"), new BigDecimal("3.50")))
                .isEqualByComparingTo("6.50");
    }

    @Test
    void isPositive_returnsTrue_forPositiveValue() {
        assertThat(Money.isPositive(new BigDecimal("0.01"))).isTrue();
    }

    @Test
    void isPositive_returnsFalse_forZero() {
        assertThat(Money.isPositive(BigDecimal.ZERO)).isFalse();
    }

    @Test
    void equals_comparesIgnoringTrailingZeros() {
        assertThat(Money.equals(new BigDecimal("10.00"), new BigDecimal("10"))).isTrue();
    }
}
