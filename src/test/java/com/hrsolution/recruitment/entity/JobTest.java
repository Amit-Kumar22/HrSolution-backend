package com.hrsolution.recruitment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Visibility, expiry and the advertising labels on a job. */
class JobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private Job job;

    @BeforeEach
    void setUp() {
        job = new Job();
        job.setTitle("Security Guard");
        job.setStatus(JobStatus.OPEN);
    }

    @Nested
    @DisplayName("public visibility")
    class Visibility {

        @Test
        @DisplayName("an open advert with no expiry is visible")
        void openAndOpenEnded() {
            assertThat(job.isOpenOn(TODAY)).isTrue();
            assertThat(job.hasExpiredOn(TODAY)).isFalse();
            assertThat(job.daysUntilExpiry(TODAY)).isNull();
        }

        @Test
        @DisplayName("an advert is visible on its closing date, not after it")
        void expiryIsInclusive() {
            // Off by one here either cuts a day of advertising or shows a
            // vacancy after applications closed.
            job.setExpiresOn(TODAY);
            assertThat(job.isOpenOn(TODAY)).isTrue();
            assertThat(job.hasExpiredOn(TODAY)).isFalse();

            assertThat(job.isOpenOn(TODAY.plusDays(1))).isFalse();
            assertThat(job.hasExpiredOn(TODAY.plusDays(1))).isTrue();
        }

        @Test
        @DisplayName("only OPEN is publicly visible")
        void onlyOpenIsAdvertised() {
            for (JobStatus status : JobStatus.values()) {
                job.setStatus(status);
                assertThat(job.isOpenOn(TODAY))
                        .as("%s", status)
                        .isEqualTo(status == JobStatus.OPEN);
            }
        }

        @Test
        @DisplayName("expiry is evaluated independently of status")
        void expiryIgnoresStatus() {
            // The status says OPEN but the date has passed. A nightly sweep
            // that missed a run would leave exactly this state, which is why
            // expiry is checked on read.
            job.setExpiresOn(TODAY.minusDays(1));
            assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
            assertThat(job.hasExpiredOn(TODAY)).isTrue();
            assertThat(job.isOpenOn(TODAY)).isFalse();
        }

        @Test
        @DisplayName("days until expiry counts down and then reads null")
        void countdown() {
            job.setExpiresOn(TODAY.plusDays(10));
            assertThat(job.daysUntilExpiry(TODAY)).isEqualTo(10);

            job.setExpiresOn(TODAY.minusDays(1));
            assertThat(job.daysUntilExpiry(TODAY)).isNull();
        }
    }

    @Nested
    @DisplayName("salary label")
    class SalaryLabel {

        @Test
        @DisplayName("null when nothing is disclosed")
        void undisclosed() {
            assertThat(job.salaryRangeLabel()).isNull();
        }

        @Test
        @DisplayName("renders a range")
        void range() {
            job.setSalaryMin(new BigDecimal("18000.00"));
            job.setSalaryMax(new BigDecimal("22000.00"));
            assertThat(job.salaryRangeLabel()).isEqualTo("Rs 18000 - Rs 22000 per month");
        }

        @Test
        @DisplayName("collapses an equal range to a single figure")
        void equalBounds() {
            job.setSalaryMin(new BigDecimal("18000.00"));
            job.setSalaryMax(new BigDecimal("18000.00"));
            assertThat(job.salaryRangeLabel()).isEqualTo("Rs 18000 per month");
        }

        @Test
        @DisplayName("handles a half-open range from either side")
        void halfOpen() {
            job.setSalaryMin(new BigDecimal("18000.00"));
            assertThat(job.salaryRangeLabel()).isEqualTo("From Rs 18000 per month");

            job.setSalaryMin(null);
            job.setSalaryMax(new BigDecimal("22000.00"));
            assertThat(job.salaryRangeLabel()).isEqualTo("Up to Rs 22000 per month");
        }
    }

    @Nested
    @DisplayName("experience label")
    class ExperienceLabel {

        @Test
        @DisplayName("null when unstated")
        void unstated() {
            assertThat(job.experienceLabel()).isNull();
        }

        @Test
        @DisplayName("says so plainly when freshers are welcome")
        void freshers() {
            // "0 months - null" would otherwise render as "At least 0 months",
            // which reads as an oversight rather than an invitation.
            job.setExperienceMinMonths(0);
            assertThat(job.experienceLabel()).isEqualTo("No experience required");
        }

        @Test
        @DisplayName("renders whole years, and years with months")
        void years() {
            job.setExperienceMinMonths(12);
            job.setExperienceMaxMonths(60);
            assertThat(job.experienceLabel()).isEqualTo("1 year - 5 years");

            job.setExperienceMinMonths(18);
            job.setExperienceMaxMonths(30);
            assertThat(job.experienceLabel()).isEqualTo("1 year 6 months - 2 years 6 months");
        }

        @Test
        @DisplayName("renders sub-year requirements in months")
        void months() {
            job.setExperienceMinMonths(6);
            assertThat(job.experienceLabel()).isEqualTo("At least 6 months");

            job.setExperienceMinMonths(1);
            assertThat(job.experienceLabel()).isEqualTo("At least 1 month");
        }
    }

    @Nested
    @DisplayName("JobStatus behaviour")
    class StatusBehaviour {

        @Test
        @DisplayName("only OPEN accepts applications")
        void onlyOpenAccepts() {
            for (JobStatus status : JobStatus.values()) {
                assertThat(status.acceptsApplications())
                        .as("%s", status)
                        .isEqualTo(status == JobStatus.OPEN);
            }
        }

        @Test
        @DisplayName("CLOSED and FILLED are the finished states")
        void finishedStates() {
            assertThat(JobStatus.CLOSED.isFinished()).isTrue();
            assertThat(JobStatus.FILLED.isFinished()).isTrue();
            assertThat(JobStatus.ON_HOLD.isFinished()).isFalse();
            assertThat(JobStatus.DRAFT.isFinished()).isFalse();
            assertThat(JobStatus.OPEN.isFinished()).isFalse();
        }

        @Test
        @DisplayName("ON_HOLD hides the advert without finishing the job")
        void onHoldIsAPause() {
            // The client has paused, not cancelled, so existing applications
            // stay live while the advert comes down.
            assertThat(JobStatus.ON_HOLD.isPubliclyVisible()).isFalse();
            assertThat(JobStatus.ON_HOLD.acceptsApplications()).isFalse();
            assertThat(JobStatus.ON_HOLD.isFinished()).isFalse();
        }
    }
}
