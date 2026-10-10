package com.hrsolution.client.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service charge arithmetic and contract date logic.
 *
 * <p>{@link #serviceChargeFor} ends up on a real invoice, so the cases below use
 * plausible commercial numbers rather than round ones.
 */
class ClientContractTest {

    private ClientContract contract(ServiceChargeType type, String value,
                                    LocalDate start, LocalDate end) {
        ClientContract contract = new ClientContract();
        contract.setServiceChargeType(type);
        contract.setServiceChargeValue(new BigDecimal(value));
        contract.setStartDate(start);
        contract.setEndDate(end);
        contract.setStatus(ContractStatus.ACTIVE);
        return contract;
    }

    @Nested
    @DisplayName("service charge")
    class ServiceCharge {

        @ParameterizedTest(name = "{1}% of a {0} wage bill = {2}")
        @CsvSource({
                "100000.00, 8.5,  8500.00",
                "100000.00, 5,    5000.00",
                "847500.00, 12.5, 105937.50",
                "100000.00, 0,    0.00"
        })
        void percentageOfWages(String wages, String percent, String expected) {
            ClientContract contract = contract(ServiceChargeType.PERCENTAGE, percent, null, null);

            // The worker count is irrelevant on a percentage deal.
            assertThat(contract.serviceChargeFor(new BigDecimal(wages), 25))
                    .isEqualByComparingTo(expected);
        }

        @ParameterizedTest(name = "{0} per worker x {1} workers = {2}")
        @CsvSource({
                "1200, 25, 30000.00",
                "1500, 1,  1500.00",
                "850,  137, 116450.00",
                "0,    25, 0.00"
        })
        void fixedPerWorker(String perWorker, int workers, String expected) {
            ClientContract contract =
                    contract(ServiceChargeType.FIXED_PER_WORKER, perWorker, null, null);

            // The wage bill is irrelevant on a fixed deal - which is exactly why
            // the type is stored rather than inferred from the value.
            assertThat(contract.serviceChargeFor(new BigDecimal("999999"), workers))
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("the same value means very different money under each type")
        void sameValueDifferentMeaning() {
            BigDecimal wages = new BigDecimal("500000");
            int workers = 40;

            BigDecimal asPercent = contract(ServiceChargeType.PERCENTAGE, "8", null, null)
                    .serviceChargeFor(wages, workers);
            BigDecimal asFixed = contract(ServiceChargeType.FIXED_PER_WORKER, "8", null, null)
                    .serviceChargeFor(wages, workers);

            // 40,000 versus 320. Inferring the type from the magnitude would be
            // a guess with a 100x error attached.
            assertThat(asPercent).isEqualByComparingTo("40000.00");
            assertThat(asFixed).isEqualByComparingTo("320.00");
        }

        @Test
        @DisplayName("a negative worker count is floored at zero, not negated")
        void negativeWorkerCountFloored() {
            ClientContract contract =
                    contract(ServiceChargeType.FIXED_PER_WORKER, "1200", null, null);

            // A bad count must not produce a credit on an invoice.
            assertThat(contract.serviceChargeFor(BigDecimal.ZERO, -5))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("a null charge value yields zero rather than failing a billing run")
        void nullValueYieldsZero() {
            ClientContract contract = new ClientContract();
            contract.setServiceChargeType(ServiceChargeType.PERCENTAGE);

            assertThat(contract.serviceChargeFor(new BigDecimal("100000"), 10))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("rounds half up to paise, like every other money figure")
        void roundsHalfUp() {
            // 7.77% of 12,345.67 = 959.258... -> 959.26
            ClientContract contract = contract(ServiceChargeType.PERCENTAGE, "7.77", null, null);

            assertThat(contract.serviceChargeFor(new BigDecimal("12345.67"), 1))
                    .isEqualByComparingTo("959.26");
        }
    }

    @Nested
    @DisplayName("date coverage")
    class DateCoverage {

        private static final LocalDate APRIL_1 = LocalDate.of(2026, 4, 1);
        private static final LocalDate MARCH_31 = LocalDate.of(2027, 3, 31);

        @Test
        @DisplayName("covers its start and end dates inclusively")
        void inclusiveBounds() {
            ClientContract contract =
                    contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, MARCH_31);

            assertThat(contract.coversDate(APRIL_1)).isTrue();
            assertThat(contract.coversDate(MARCH_31)).isTrue();
            assertThat(contract.coversDate(APRIL_1.minusDays(1))).isFalse();
            assertThat(contract.coversDate(MARCH_31.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("an open-ended contract covers every date from its start")
        void openEndedCoversEverythingAfterStart() {
            ClientContract contract =
                    contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, null);

            assertThat(contract.coversDate(APRIL_1)).isTrue();
            assertThat(contract.coversDate(LocalDate.of(2099, 1, 1))).isTrue();
            assertThat(contract.coversDate(APRIL_1.minusDays(1))).isFalse();
        }

        @Test
        @DisplayName("isInForceOn requires ACTIVE as well as covering the date")
        void statusMatters() {
            ClientContract contract =
                    contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, MARCH_31);

            assertThat(contract.isInForceOn(APRIL_1)).isTrue();

            // A draft covers the date but binds nobody.
            contract.setStatus(ContractStatus.DRAFT);
            assertThat(contract.coversDate(APRIL_1)).isTrue();
            assertThat(contract.isInForceOn(APRIL_1)).isFalse();

            contract.setStatus(ContractStatus.TERMINATED);
            assertThat(contract.isInForceOn(APRIL_1)).isFalse();
        }

        @Test
        @DisplayName("overlap detection catches a one-day collision")
        void overlapDetection() {
            ClientContract first = contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, MARCH_31);
            ClientContract touching = contract(ServiceChargeType.PERCENTAGE, "9",
                    MARCH_31, MARCH_31.plusYears(1));
            ClientContract consecutive = contract(ServiceChargeType.PERCENTAGE, "9",
                    MARCH_31.plusDays(1), MARCH_31.plusYears(1));

            // Sharing a single day still means two service charges could apply
            // to that day's billing.
            assertThat(first.overlaps(touching)).isTrue();
            assertThat(first.overlaps(consecutive)).isFalse();
        }

        @Test
        @DisplayName("an open-ended contract blocks every later one")
        void openEndedBlocksSuccessors() {
            ClientContract openEnded =
                    contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, null);
            ClientContract later = contract(ServiceChargeType.PERCENTAGE, "9",
                    LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31));

            // Correct, and worth knowing: an open-ended contract has to be given
            // an end date or terminated before a successor can be activated.
            assertThat(openEnded.overlaps(later)).isTrue();
        }

        @Test
        @DisplayName("reports days until expiry, and null when open-ended or past")
        void daysUntilExpiry() {
            ClientContract fixedTerm =
                    contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, MARCH_31);

            assertThat(fixedTerm.daysUntilExpiry(MARCH_31.minusDays(30))).isEqualTo(30);
            assertThat(fixedTerm.daysUntilExpiry(MARCH_31)).isZero();
            // Already past - nothing to remind anyone about.
            assertThat(fixedTerm.daysUntilExpiry(MARCH_31.plusDays(1))).isNull();

            ClientContract openEnded = contract(ServiceChargeType.PERCENTAGE, "8", APRIL_1, null);
            assertThat(openEnded.daysUntilExpiry(APRIL_1)).isNull();
        }
    }

    @Nested
    @DisplayName("payment terms")
    class PaymentTerms {

        @Test
        @DisplayName("the contract's terms override the client's default")
        void contractOverridesClient() {
            Client client = new Client();
            client.setPaymentTermsDays(30);

            ClientContract contract = contract(ServiceChargeType.PERCENTAGE, "8", null, null);
            contract.setClient(client);
            contract.setPaymentTermsDays(45);

            assertThat(contract.effectivePaymentTermsDays()).isEqualTo(45);
        }

        @Test
        @DisplayName("falls back to the client's default when unset")
        void fallsBackToClient() {
            Client client = new Client();
            client.setPaymentTermsDays(60);

            ClientContract contract = contract(ServiceChargeType.PERCENTAGE, "8", null, null);
            contract.setClient(client);
            contract.setPaymentTermsDays(null);

            assertThat(contract.effectivePaymentTermsDays()).isEqualTo(60);
        }

        @Test
        @DisplayName("falls back to 30 days with no client attached")
        void fallsBackToDefault() {
            ClientContract contract = contract(ServiceChargeType.PERCENTAGE, "8", null, null);
            assertThat(contract.effectivePaymentTermsDays()).isEqualTo(30);
        }
    }
}
