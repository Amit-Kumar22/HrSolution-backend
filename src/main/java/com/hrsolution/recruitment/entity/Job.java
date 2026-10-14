package com.hrsolution.recruitment.entity;

import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A vacancy advertised on the public careers page.
 *
 * <h2>Why the client is not public</h2>
 *
 * <p>{@link #client} and {@link #site} record who the role is actually for, and
 * neither is ever mapped onto a public DTO. Clients in this industry routinely
 * require that a vacancy not be attributed to them - naming them tells their
 * competitors about a plant expansion, and tells their existing workforce about
 * churn. Staff need the link to route applications; the internet does not get
 * it. See {@code JobDtos.PublicResponse}, which has no client field at all.
 *
 * <h2>Expiry is evaluated, not scheduled</h2>
 *
 * <p>{@link #expiresOn} is checked on read by {@link #isOpenOn(LocalDate)}
 * rather than swept by a nightly job that flips the status. A missed cron run
 * would otherwise leave a lapsed advert collecting applications nobody intends
 * to read, and the candidate experience of applying into a void is worse than
 * the tidiness of a stored status is worth.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
public class Job extends SoftDeletableEntity {

    /** Public URL segment, e.g. {@code security-guard-pune-plant}. */
    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "summary", length = 400)
    private String summary;

    // columnDefinition rather than @Lob, matching ServiceOffering in Phase 3.
    // @Lob on a String maps to CLOB, which Hibernate 7 validates against
    // tinytext and rejects on a TEXT column - startup fails with
    // "wrong column type encountered in column [description]".
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ManpowerCategory category;

    /** Who the role is for. Never exposed publicly - see the class note. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    /** Where the role is, when it is tied to one site. Never exposed publicly. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private ClientSite site;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "openings", nullable = false)
    private int openings = 1;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "experience_min_months")
    private Integer experienceMinMonths;

    @Column(name = "experience_max_months")
    private Integer experienceMaxMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", length = 20)
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    /** Last day the advert should appear. Null means no expiry. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "meta_title", length = 160)
    private String metaTitle;

    @Column(name = "meta_description", length = 320)
    private String metaDescription;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /** Whether {@code date} is on or before the expiry, if there is one. */
    public boolean isWithinValidityOn(LocalDate date) {
        return expiresOn == null || date == null || !date.isAfter(expiresOn);
    }

    /** Whether the advert should be visible and accepting applications on a date. */
    public boolean isOpenOn(LocalDate date) {
        return status.acceptsApplications() && isWithinValidityOn(date);
    }

    /** Whether the advert has lapsed on its own, regardless of status. */
    public boolean hasExpiredOn(LocalDate date) {
        return expiresOn != null && date != null && date.isAfter(expiresOn);
    }

    /** Days until the advert lapses, or null when open-ended or already lapsed. */
    public Long daysUntilExpiry(LocalDate asOf) {
        if (expiresOn == null || asOf == null || expiresOn.isBefore(asOf)) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(asOf, expiresOn);
    }

    /**
     * "Rs 18,000 - Rs 22,000 per month", or null when undisclosed.
     *
     * <p>Composed here rather than in the client so that the public site, the
     * staff console and any future email template all read the same string.
     */
    public String salaryRangeLabel() {
        if (salaryMin == null && salaryMax == null) {
            return null;
        }
        if (salaryMin != null && salaryMax != null) {
            if (salaryMin.compareTo(salaryMax) == 0) {
                return "Rs %s per month".formatted(plain(salaryMin));
            }
            return "Rs %s - Rs %s per month".formatted(plain(salaryMin), plain(salaryMax));
        }
        return salaryMin != null
                ? "From Rs %s per month".formatted(plain(salaryMin))
                : "Up to Rs %s per month".formatted(plain(salaryMax));
    }

    /** "2 - 5 years", "At least 1 year", "No experience required", or null. */
    public String experienceLabel() {
        if (experienceMinMonths == null && experienceMaxMonths == null) {
            return null;
        }
        if (experienceMinMonths != null && experienceMinMonths == 0 && experienceMaxMonths == null) {
            return "No experience required";
        }
        if (experienceMinMonths != null && experienceMaxMonths != null) {
            return "%s - %s".formatted(years(experienceMinMonths), years(experienceMaxMonths));
        }
        return experienceMinMonths != null
                ? "At least %s".formatted(years(experienceMinMonths))
                : "Up to %s".formatted(years(experienceMaxMonths));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String years(int months) {
        if (months < 12) {
            return months == 1 ? "1 month" : months + " months";
        }
        int wholeYears = months / 12;
        int remainder = months % 12;
        String yearPart = wholeYears == 1 ? "1 year" : wholeYears + " years";
        return remainder == 0 ? yearPart : yearPart + " " + remainder + " months";
    }
}
