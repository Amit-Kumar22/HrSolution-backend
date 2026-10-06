package com.hrsolution.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Date handling for an Indian payroll system.
 *
 * <p>The storage/display split is deliberate and applies everywhere:
 * {@link Instant} timestamps are stored in UTC, and converted to
 * {@link #IST} only when formatted for a human. A payslip for March must say
 * March in Kolkata regardless of where the server runs.
 */
public final class DateUtils {

    /** The business timezone. All display formatting resolves against this. */
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** {@code dd-MM-yyyy} - the format used on screens, payslips and invoices. */
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** {@code dd-MM-yyyy HH:mm} for audit trails and timestamps. */
    public static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    /** {@code MMMM yyyy}, e.g. {@code March 2026} - payroll and invoice periods. */
    public static final DateTimeFormatter DISPLAY_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    private DateUtils() {
    }

    public static LocalDate toIstDate(Instant instant) {
        return instant == null ? null : instant.atZone(IST).toLocalDate();
    }

    public static LocalDateTime toIstDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(IST).toLocalDateTime();
    }

    public static String format(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    public static String formatIst(Instant instant) {
        return instant == null ? "" : toIstDateTime(instant).format(DISPLAY_DATE_TIME);
    }

    public static String formatMonth(YearMonth month) {
        return month == null ? "" : month.atDay(1).format(DISPLAY_MONTH);
    }

    public static LocalDate firstDayOf(YearMonth month) {
        return month.atDay(1);
    }

    public static LocalDate lastDayOf(YearMonth month) {
        return month.atEndOfMonth();
    }

    /**
     * Calendar days in the month - the denominator for monthly pro-rata wage
     * calculation, which is the convention for contract labour in India
     * (as opposed to a fixed 26 or 30 day divisor).
     */
    public static int daysIn(YearMonth month) {
        return month.lengthOfMonth();
    }

    /**
     * Whether two date ranges share at least one day.
     *
     * <p>A null end date means open-ended, i.e. still running. This is what
     * stops the same worker being deployed to two sites on overlapping dates,
     * and what detects overlapping client contracts.
     *
     * <p>Both ranges are treated as inclusive of their end date.
     */
    public static boolean overlaps(LocalDate firstStart, LocalDate firstEnd,
                                   LocalDate secondStart, LocalDate secondEnd) {
        if (firstStart == null || secondStart == null) {
            return false;
        }
        boolean firstStartsBeforeSecondEnds = secondEnd == null || !firstStart.isAfter(secondEnd);
        boolean secondStartsBeforeFirstEnds = firstEnd == null || !secondStart.isAfter(firstEnd);
        return firstStartsBeforeSecondEnds && secondStartsBeforeFirstEnds;
    }

    /**
     * The Indian financial year containing {@code date}, as {@code "2026-27"}.
     *
     * <p>The year runs 1 April to 31 March, and this string forms part of the
     * invoice number series ({@code INV/2026-27/0001}).
     */
    public static String financialYear(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return "%d-%02d".formatted(startYear, (startYear + 1) % 100);
    }
}
