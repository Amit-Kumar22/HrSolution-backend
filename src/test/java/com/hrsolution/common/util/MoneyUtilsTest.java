package com.hrsolution.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These are the arithmetic primitives every payroll and invoice figure is built
 * from, so the cases below use real statutory rates and wage ceilings rather
 * than arbitrary numbers.
 */
class MoneyUtilsTest {

    @Nested
    @DisplayName("scale")
    class Scale {

        @Test
        @DisplayName("rounds half up, not half even")
        void roundsHalfUp() {
            // BigDecimal's default for divide is HALF_EVEN, which would give
            // 10.00 here. Payroll convention is HALF_UP.
            assertThat(MoneyUtils.scale(new BigDecimal("10.005")))
                    .isEqualByComparingTo("10.01");
            assertThat(MoneyUtils.scale(new BigDecimal("10.015")))
                    .isEqualByComparingTo("10.02");
        }

        @Test
        @DisplayName("treats null as zero")
        void nullIsZero() {
            assertThat(MoneyUtils.scale(null)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("always produces exactly two decimal places")
        void alwaysTwoDecimals() {
            assertThat(MoneyUtils.scale(new BigDecimal("7"))).hasToString("7.00");
        }
    }

    @Nested
    @DisplayName("percentOf")
    class PercentOf {

        @ParameterizedTest(name = "{2}% of {1} = {0}")
        @CsvSource({
                // base, percent, expected - current statutory rates
                "1800.00, 15000, 12",      // PF employee contribution at the ceiling
                "157.50,  21000, 0.75",    // ESI employee contribution at the wage limit
                "682.50,  21000, 3.25",    // ESI employer contribution
                "1800.00, 20000, 9",       // GST CGST half of 18%
                "3600.00, 20000, 18",      // GST at 18%
                "0.00,    10000, 0"
        })
        void computesStatutoryPercentages(String expected, String base, String percent) {
            assertThat(MoneyUtils.percentOf(new BigDecimal(base), new BigDecimal(percent)))
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("returns zero when either operand is null")
        void nullOperands() {
            assertThat(MoneyUtils.percentOf(null, BigDecimal.TEN)).isEqualByComparingTo("0.00");
            assertThat(MoneyUtils.percentOf(BigDecimal.TEN, null)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("keeps intermediate precision before the final rounding")
        void intermediatePrecision() {
            // 1.75% of 13333 is 233.3275. Rounding the intermediate division to
            // 2dp first would give 233.32; rounding only at the end gives 233.33.
            assertThat(MoneyUtils.percentOf(new BigDecimal("13333"), new BigDecimal("1.75")))
                    .isEqualByComparingTo("233.33");
        }
    }

    @Nested
    @DisplayName("proRata")
    class ProRata {

        @Test
        @DisplayName("pro-rates a monthly wage over paid days")
        void proRatesOverPaidDays() {
            assertThat(MoneyUtils.proRata(new BigDecimal("15000"), 26, 30))
                    .isEqualByComparingTo("13000.00");
        }

        @Test
        @DisplayName("rounds the result half up")
        void rounds() {
            // 15000 * 17 / 31 = 8225.80645...
            assertThat(MoneyUtils.proRata(new BigDecimal("15000"), 17, 31))
                    .isEqualByComparingTo("8225.81");
        }

        @Test
        @DisplayName("full attendance returns the full monthly amount")
        void fullMonth() {
            assertThat(MoneyUtils.proRata(new BigDecimal("18500"), 31, 31))
                    .isEqualByComparingTo("18500.00");
        }

        @Test
        @DisplayName("zero paid days yields zero, not an error")
        void zeroPaidDays() {
            assertThat(MoneyUtils.proRata(new BigDecimal("15000"), 0, 30))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("zero total days yields zero instead of dividing by zero")
        void zeroTotalDays() {
            // A payroll run for a period with no attendance days must produce a
            // zero line rather than blowing up the whole run.
            assertThat(MoneyUtils.proRata(new BigDecimal("15000"), 10, 0))
                    .isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("capAt")
    class CapAt {

        @Test
        @DisplayName("caps a wage above the ceiling")
        void capsAboveCeiling() {
            // PF is computed on a maximum wage of 15000 however much is earned.
            assertThat(MoneyUtils.capAt(new BigDecimal("25000"), new BigDecimal("15000")))
                    .isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("leaves a wage below the ceiling untouched")
        void belowCeiling() {
            assertThat(MoneyUtils.capAt(new BigDecimal("12000"), new BigDecimal("15000")))
                    .isEqualByComparingTo("12000.00");
        }

        @Test
        @DisplayName("a null ceiling means uncapped")
        void nullCeiling() {
            assertThat(MoneyUtils.capAt(new BigDecimal("99000"), null))
                    .isEqualByComparingTo("99000.00");
        }
    }

    @Nested
    @DisplayName("arithmetic helpers")
    class Arithmetic {

        @Test
        @DisplayName("sum skips nulls rather than failing")
        void sumSkipsNulls() {
            assertThat(MoneyUtils.sum(new BigDecimal("100.50"), null, new BigDecimal("49.50")))
                    .isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("sum of nothing is zero")
        void sumOfNothing() {
            assertThat(MoneyUtils.sum()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("divide by zero yields zero")
        void divideByZero() {
            assertThat(MoneyUtils.divide(new BigDecimal("100"), BigDecimal.ZERO))
                    .isEqualByComparingTo("0.00");
            assertThat(MoneyUtils.divide(new BigDecimal("100"), null))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("subtract and multiply are null-safe")
        void nullSafeOperations() {
            assertThat(MoneyUtils.subtract(new BigDecimal("500"), null)).isEqualByComparingTo("500.00");
            assertThat(MoneyUtils.subtract(null, new BigDecimal("500"))).isEqualByComparingTo("-500.00");
            assertThat(MoneyUtils.multiply(new BigDecimal("12.5"), new BigDecimal("4")))
                    .isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("floorAtZero stops a net pay printing as negative")
        void floorAtZero() {
            // Deductions can exceed earnings in an exit month; the payslip must
            // show 0.00 and the balance must be carried as a recovery instead.
            assertThat(MoneyUtils.floorAtZero(new BigDecimal("-250.75")))
                    .isEqualByComparingTo("0.00");
            assertThat(MoneyUtils.floorAtZero(new BigDecimal("250.75")))
                    .isEqualByComparingTo("250.75");
        }

        @Test
        @DisplayName("sign checks treat null as zero")
        void signChecks() {
            assertThat(MoneyUtils.isZero(null)).isTrue();
            assertThat(MoneyUtils.isZero(new BigDecimal("0.00"))).isTrue();
            assertThat(MoneyUtils.isPositive(new BigDecimal("0.01"))).isTrue();
            assertThat(MoneyUtils.isPositive(null)).isFalse();
            assertThat(MoneyUtils.isNegative(new BigDecimal("-0.01"))).isTrue();
        }
    }
}
