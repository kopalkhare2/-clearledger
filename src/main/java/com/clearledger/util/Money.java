package com.clearledger.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for safe monetary arithmetic using BigDecimal.
 *
 * Policy:
 *   scale       = 2 (cents precision)
 *   rounding    = HALF_EVEN (banker's rounding — reduces cumulative rounding bias)
 *
 * All monetary values in ClearLedger must be scaled through this class
 * before persistence or comparison.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private Money() {}

    /** Normalize a BigDecimal to the canonical monetary scale. */
    public static BigDecimal of(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Monetary value cannot be null");
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return of(a).add(of(b));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return of(a).subtract(of(b));
    }

    public static boolean isPositive(BigDecimal value) {
        return of(value).compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isZeroOrNegative(BigDecimal value) {
        return of(value).compareTo(BigDecimal.ZERO) <= 0;
    }

    public static boolean equals(BigDecimal a, BigDecimal b) {
        return of(a).compareTo(of(b)) == 0;
    }
}
