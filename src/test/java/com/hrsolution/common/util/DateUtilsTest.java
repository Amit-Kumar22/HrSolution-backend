package com.hrsolution.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Nested
    @DisplayName("overlaps")
    class Overlaps {

        @Test
        @DisplayName("detects a partial overlap")
        void partialOverlap() {
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                    LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 15)))
                    .isTrue();
        }

        @Test
        @DisplayName("a single shared day counts as an overlap")
        void touchingRangesOverlap() {
            // Both ranges are inclusive of their end date, so one range ending
            // on the day the next begins IS a double deployment for that day.
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                    LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 30)))
                    .isTrue();
        }

        @Test
        @DisplayName("consecutive ranges do not overlap")
        void consecutiveRanges() {
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                    .isFalse();
        }

        @Test
        @DisplayName("fully disjoint ranges do not overlap, in either argument order")
        void disjoint() {
            LocalDate aStart = LocalDate.of(2026, 1, 1);
            LocalDate aEnd = LocalDate.of(2026, 1, 31);
            LocalDate bStart = LocalDate.of(2026, 6, 1);
            LocalDate bEnd = LocalDate.of(2026, 6, 30);
            assertThat(DateUtils.overlaps(aStart, aEnd, bStart, bEnd)).isFalse();
            assertThat(DateUtils.overlaps(bStart, bEnd, aStart, aEnd)).isFalse();
        }

        @Test
        @DisplayName("an open-ended existing deployment blocks any later start")
        void openEndedExisting() {
            // This is the common real case: a worker is deployed with no end date
            // and someone tries to deploy them elsewhere next month.
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 4, 1), null,
                    LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)))
                    .isTrue();
        }

        @Test
        @DisplayName("an open-ended new deployment overlaps an earlier closed one it reaches")
        void openEndedNew() {
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    LocalDate.of(2026, 6, 1), null))
                    .isTrue();
        }

        @Test
        @DisplayName("an open-ended range starting after a closed range ends does not overlap")
        void openEndedAfterClosed() {
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                    LocalDate.of(2026, 4, 1), null))
                    .isFalse();
        }

        @Test
        @DisplayName("two open-ended ranges always overlap")
        void bothOpenEnded() {
            assertThat(DateUtils.overlaps(
                    LocalDate.of(2026, 1, 1), null,
                    LocalDate.of(2030, 1, 1), null))
                    .isTrue();
        }

        @Test
        @DisplayName("a null start means no range, so no overlap")
        void nullStart() {
            assertThat(DateUtils.overlaps(null, null, LocalDate.of(2026, 1, 1), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("financialYear")
    class FinancialYear {

        @ParameterizedTest(name = "{0} falls in FY {1}")
        @CsvSource({
                "2026-04-01, 2026-27",   // first day of the year
                "2026-12-15, 2026-27",
                "2027-03-31, 2026-27",   // last day of the year
                "2026-03-31, 2025-26",   // day before it starts
                "2027-01-01, 2026-27",   // January belongs to the previous April
                "2026-01-01, 2025-26"
        })
        void resolvesAprilToMarchYear(LocalDate date, String expected) {
            assertThat(DateUtils.financialYear(date)).isEqualTo(expected);
        }

        @Test
        @DisplayName("pads the century rollover to two digits")
        void centuryRollover() {
            assertThat(DateUtils.financialYear(LocalDate.of(2099, 5, 1))).isEqualTo("2099-00");
        }
    }

    @Nested
    @DisplayName("month helpers")
    class MonthHelpers {

        @Test
        @DisplayName("resolves first and last day, including a leap February")
        void monthBounds() {
            YearMonth february2028 = YearMonth.of(2028, 2);
            assertThat(DateUtils.firstDayOf(february2028)).isEqualTo(LocalDate.of(2028, 2, 1));
            assertThat(DateUtils.lastDayOf(february2028)).isEqualTo(LocalDate.of(2028, 2, 29));
            assertThat(DateUtils.daysIn(february2028)).isEqualTo(29);
        }

        @Test
        @DisplayName("counts calendar days, the denominator for monthly pro-rata")
        void calendarDays() {
            assertThat(DateUtils.daysIn(YearMonth.of(2026, 4))).isEqualTo(30);
            assertThat(DateUtils.daysIn(YearMonth.of(2026, 1))).isEqualTo(31);
            assertThat(DateUtils.daysIn(YearMonth.of(2026, 2))).isEqualTo(28);
        }
    }

    @Nested
    @DisplayName("IST conversion and formatting")
    class Formatting {

        @Test
        @DisplayName("converts a UTC instant to the Indian calendar date")
        void convertsToIstDate() {
            // 23:30 UTC is already the next day at 05:00 in Kolkata (+05:30).
            // Getting this wrong puts an attendance entry in the wrong month.
            Instant lateEveningUtc = Instant.parse("2026-03-31T23:30:00Z");
            assertThat(DateUtils.toIstDate(lateEveningUtc)).isEqualTo(LocalDate.of(2026, 4, 1));
        }

        @Test
        @DisplayName("formats dates as dd-MM-yyyy")
        void formatsDate() {
            assertThat(DateUtils.format(LocalDate.of(2026, 10, 6))).isEqualTo("06-10-2026");
        }

        @Test
        @DisplayName("formats instants in IST, not UTC")
        void formatsInstantInIst() {
            assertThat(DateUtils.formatIst(Instant.parse("2026-10-06T06:30:00Z")))
                    .isEqualTo("06-10-2026 12:00");
        }

        @Test
        @DisplayName("formats a payroll period as month and year")
        void formatsMonth() {
            assertThat(DateUtils.formatMonth(YearMonth.of(2026, 3))).isEqualTo("March 2026");
        }

        @Test
        @DisplayName("formatting null gives an empty string, never the text null")
        void nullsFormatAsEmpty() {
            assertThat(DateUtils.format(null)).isEmpty();
            assertThat(DateUtils.formatIst(null)).isEmpty();
            assertThat(DateUtils.formatMonth(null)).isEmpty();
            assertThat(DateUtils.toIstDate(null)).isNull();
        }
    }
}
