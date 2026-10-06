package com.hrsolution.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every rupee amount in this system flows through here.
 *
 * <p>Two rules, applied without exception: money is {@link BigDecimal} (never
 * {@code double}), and it is rounded to {@value #SCALE} decimal places with
 * {@link RoundingMode#HALF_UP}. Mixing rounding modes across payroll and
 * invoicing is how a salary register stops reconciling with the GST return.
 *
 * <p>Round at the point a figure becomes an amount someone is paid or billed -
 * not in the middle of a chain of intermediate multiplications, where early
 * rounding compounds.
 */
public final class MoneyUtils {

    /** Decimal places for all stored and displayed money. */
    public static final int SCALE = 2;

    /** The project-wide rounding mode. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtils() {
    }

    /** Rounds to 2dp, HALF_UP. Null becomes {@link #ZERO}. */
    public static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    /** Null-safe accessor that leaves the scale untouched. */
    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** Adds any number of possibly-null amounts and rounds the total. */
    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(nullToZero(value));
        }
        return scale(total);
    }

    public static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
        return scale(nullToZero(minuend).subtract(nullToZero(subtrahend)));
    }

    public static BigDecimal multiply(BigDecimal multiplicand, BigDecimal multiplier) {
        return scale(nullToZero(multiplicand).multiply(nullToZero(multiplier)));
    }

    /** Division guarding against a zero or null divisor, which yields {@link #ZERO}. */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO;
        }
        return nullToZero(dividend).divide(divisor, SCALE, ROUNDING);
    }

    /**
     * {@code percent}% of {@code base} - the workhorse for PF, ESI, GST and
     * contract service charges.
     *
     * <p>{@code percentOf(10000, 12)} is {@code 1200.00}.
     */
    public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        if (base == null || percent == null) {
            return ZERO;
        }
        return scale(base.multiply(percent).divide(HUNDRED, SCALE + 4, ROUNDING));
    }

    /**
     * Pro-rates a monthly amount over the days actually paid.
     *
     * <p>{@code proRata(15000, 26, 30)} is {@code 13000.00}. A
     * {@code totalDays} of zero yields {@link #ZERO} rather than dividing by zero,
     * which matters because a payroll run for a month with no attendance days
     * must produce a zero line, not an error.
     */
    public static BigDecimal proRata(BigDecimal monthlyAmount, int paidDays, int totalDays) {
        if (monthlyAmount == null || totalDays <= 0 || paidDays <= 0) {
            return ZERO;
        }
        return scale(monthlyAmount
                .multiply(BigDecimal.valueOf(paidDays))
                .divide(BigDecimal.valueOf(totalDays), SCALE + 4, ROUNDING));
    }

    /** Caps a value at a ceiling, used for the PF and ESI wage ceilings. */
    public static BigDecimal capAt(BigDecimal value, BigDecimal ceiling) {
        if (ceiling == null) {
            return scale(value);
        }
        BigDecimal safe = nullToZero(value);
        return scale(safe.compareTo(ceiling) > 0 ? ceiling : safe);
    }

    public static boolean isZero(BigDecimal value) {
        return nullToZero(value).compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return nullToZero(value).compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return nullToZero(value).compareTo(BigDecimal.ZERO) < 0;
    }

    /** Clamps a negative amount to zero - a net pay must never print as negative. */
    public static BigDecimal floorAtZero(BigDecimal value) {
        return isNegative(value) ? ZERO : scale(value);
    }
}
