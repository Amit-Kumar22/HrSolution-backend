package com.hrsolution.client.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RateCardTest {

    private static final LocalDate APRIL_1 = LocalDate.of(2026, 4, 1);

    private RateCard rateCard(String wage, String billing, LocalDate from, LocalDate to) {
        RateCard rateCard = new RateCard();
        rateCard.setMonthlyWage(new BigDecimal(wage));
        rateCard.setBillingRate(new BigDecimal(billing));
        rateCard.setEffectiveFrom(from);
        rateCard.setEffectiveTo(to);
        return rateCard;
    }

    @Test
    @DisplayName("gross margin is billing minus wage")
    void grossMargin() {
        assertThat(rateCard("18000", "22500", APRIL_1, null).grossMarginPerWorker())
                .isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("flags a rate that bills below the wage")
    void flagsBillingBelowWage() {
        RateCard sensible = rateCard("18000", "22500", APRIL_1, null);
        RateCard inverted = rateCard("22500", "18000", APRIL_1, null);

        assertThat(sensible.isBillingBelowWage()).isFalse();
        // Allowed - a loss-leader on one category of a large account is a real
        // commercial decision - but surfaced, because it is usually a typo.
        assertThat(inverted.isBillingBelowWage()).isTrue();
        assertThat(inverted.grossMarginPerWorker()).isEqualByComparingTo("-4500.00");
    }

    @Test
    @DisplayName("billing exactly equal to the wage is not flagged")
    void equalIsNotBelow() {
        assertThat(rateCard("18000", "18000", APRIL_1, null).isBillingBelowWage()).isFalse();
    }

    @Test
    @DisplayName("effectiveness is inclusive of both bounds")
    void effectivenessIsInclusive() {
        LocalDate end = LocalDate.of(2026, 9, 30);
        RateCard closed = rateCard("18000", "22500", APRIL_1, end);

        assertThat(closed.isEffectiveOn(APRIL_1)).isTrue();
        assertThat(closed.isEffectiveOn(end)).isTrue();
        assertThat(closed.isEffectiveOn(APRIL_1.minusDays(1))).isFalse();
        assertThat(closed.isEffectiveOn(end.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("an open row is current and covers every later date")
    void openRowIsCurrent() {
        RateCard open = rateCard("18000", "22500", APRIL_1, null);

        assertThat(open.isCurrent()).isTrue();
        assertThat(open.isEffectiveOn(LocalDate.of(2099, 1, 1))).isTrue();

        RateCard closed = rateCard("18000", "22500", APRIL_1, LocalDate.of(2026, 9, 30));
        assertThat(closed.isCurrent()).isFalse();
    }

    @Test
    @DisplayName("closing a row ends it the day before its successor starts")
    void closeBeforeLeavesNoGapAndNoOverlap() {
        RateCard predecessor = rateCard("18000", "22500", APRIL_1, null);
        LocalDate successorStart = LocalDate.of(2026, 10, 1);

        predecessor.closeBefore(successorStart);

        // The day before, not the same day: an inclusive end equal to the
        // successor's start would make both rows claim 1 October, and the
        // applicable wage would depend on row order.
        assertThat(predecessor.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(predecessor.isEffectiveOn(LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(predecessor.isEffectiveOn(successorStart)).isFalse();
    }

    @Test
    @DisplayName("a null site means the rate covers every site of the client")
    void siteSpecificity() {
        RateCard clientWide = rateCard("18000", "22500", APRIL_1, null);
        assertThat(clientWide.isSiteSpecific()).isFalse();

        RateCard siteRate = rateCard("19000", "23500", APRIL_1, null);
        siteRate.setSite(new ClientSite());
        assertThat(siteRate.isSiteSpecific()).isTrue();
    }
}
