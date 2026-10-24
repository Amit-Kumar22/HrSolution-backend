package com.hrsolution.worker.entity;

import com.hrsolution.common.crypto.EncryptedString;
import com.hrsolution.common.crypto.SensitiveValues;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.recruitment.entity.Candidate;
import com.hrsolution.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;

/**
 * A person on the payroll, supplied to client sites.
 *
 * <h2>The encrypted columns</h2>
 *
 * <p>{@link #aadhaar} and {@link #pan} go through
 * {@link EncryptedString}, so the entity deals in plaintext while the database
 * holds AES-256-GCM ciphertext. Each has a sibling {@code *Hash} column holding
 * an HMAC blind index, because the random IV makes the ciphertext
 * non-deterministic and therefore unsearchable - duplicate detection runs on
 * the hash. The hashes are maintained by {@code WorkerService}, not here:
 * computing them needs the crypto service, and an entity that reached for a
 * Spring bean would be an entity that could not be unit tested.
 *
 * <p><strong>Getting the plaintext out of this object is not the same as being
 * allowed to see it.</strong> {@link #getAadhaar()} returns the real number to
 * anything holding the entity; the {@code WORKER_SENSITIVE_READ} check lives in
 * the service, which decides between the real value and
 * {@link #maskedAadhaar()}. Any new read path has to make that decision
 * explicitly - there is no safe default, which is why the mapper refuses to
 * map these fields at all.
 *
 * <h2>employee_code is derived</h2>
 *
 * <p>{@link #employeeCode()} composes {@code EMP-2026-00042} from the joining
 * year and the id rather than storing it, the same reasoning as
 * {@code Client.clientCode()}: a second source of truth for an identifier is a
 * second thing that can disagree. It is not a statutory series and does not
 * need to be gapless.
 */
@Entity
@Table(name = "workers")
@Getter
@Setter
public class Worker extends SoftDeletableEntity {

    /** The WORKER-role login, where the worker has one. Most operatives do not. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * The application this worker was hired from.
     *
     * <p>Set when a {@code JOINED} application is converted. It is the evidence
     * that the hire was screened and interviewed, which Phase 10 may be asked
     * to produce for the principal employer.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Required on the CLRA and PF registers, so collected even though it is archaic. */
    @Column(name = "father_or_husband_name", length = 160)
    private String fatherOrHusbandName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    // ---------- Encrypted. Read the class note before touching these. ----------

    @Convert(converter = EncryptedString.class)
    @Column(name = "aadhaar_encrypted", length = 255)
    private String aadhaar;

    /** HMAC blind index of {@link #aadhaar}. Maintained by {@code WorkerService}. */
    @Column(name = "aadhaar_hash", length = 64)
    private String aadhaarHash;

    @Convert(converter = EncryptedString.class)
    @Column(name = "pan_encrypted", length = 255)
    private String pan;

    /** HMAC blind index of {@link #pan}. Maintained by {@code WorkerService}. */
    @Column(name = "pan_hash", length = 64)
    private String panHash;

    // ---------- Statutory identifiers, stored in clear ----------
    //
    // Not secret: they appear on every monthly return and are useless without
    // the underlying account. Encrypting them would cost the ability to
    // reconcile a PF challan without buying anything.

    @Column(name = "uan", length = 12)
    private String uan;

    @Column(name = "esic_ip_number", length = 10)
    private String esicIpNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerStatus status = WorkerStatus.ON_BENCH;

    @Enumerated(EnumType.STRING)
    @Column(name = "police_verification_status", nullable = false, length = 20)
    private PoliceVerificationStatus policeVerificationStatus =
            PoliceVerificationStatus.NOT_STARTED;

    @Column(name = "police_verification_date")
    private LocalDate policeVerificationDate;

    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relation", length = 60)
    private String emergencyContactRelation;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Column(name = "exit_reason", length = 500)
    private String exitReason;

    @Column(name = "full_and_final_settled", nullable = false)
    private boolean fullAndFinalSettled = false;

    @Column(name = "notes", length = 2000)
    private String notes;

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /** {@code EMP-2026-00042}. Derived - see the class note. */
    public String employeeCode() {
        if (getId() == null) {
            return null;
        }
        int year = dateOfJoining == null ? 0 : dateOfJoining.getYear();
        return "EMP-%d-%05d".formatted(year, getId());
    }

    public String fullName() {
        return lastName == null || lastName.isBlank()
                ? firstName
                : firstName + " " + lastName;
    }

    /** "EMP-2026-00042 — Rakesh Shinde", for dropdowns and deployment lists. */
    public String displayLabel() {
        return "%s — %s".formatted(employeeCode(), fullName());
    }

    public String maskedAadhaar() {
        return SensitiveValues.maskAadhaar(aadhaar);
    }

    public String maskedPan() {
        return SensitiveValues.maskPan(pan);
    }

    /** Age in whole years on a date, or null when the date of birth is unknown. */
    public Integer ageOn(LocalDate date) {
        if (dateOfBirth == null || date == null || date.isBefore(dateOfBirth)) {
            return null;
        }
        return Period.between(dateOfBirth, date).getYears();
    }

    /**
     * Whether this person is old enough to be employed at all.
     *
     * <p>14 is the floor under the Child Labour (Prohibition and Regulation)
     * Act, and 18 for the hazardous work most of these placements involve.
     * {@code WorkerService} refuses a date of birth that makes the worker under
     * 18 on their joining date.
     */
    public boolean isOfLegalWorkingAgeOn(LocalDate date) {
        Integer age = ageOn(date);
        return age == null || age >= MINIMUM_WORKING_AGE;
    }

    /** Completed months of service on a date - the basis for gratuity eligibility. */
    public Integer serviceMonthsOn(LocalDate date) {
        if (dateOfJoining == null || date == null || date.isBefore(dateOfJoining)) {
            return null;
        }
        LocalDate end = lastWorkingDay != null && lastWorkingDay.isBefore(date)
                ? lastWorkingDay
                : date;
        Period period = Period.between(dateOfJoining, end);
        return period.getYears() * 12 + period.getMonths();
    }

    /** Records an exit, which is terminal for employment purposes. */
    public void recordExit(WorkerStatus exitStatus, LocalDate lastDay, String reason) {
        this.status = exitStatus;
        this.lastWorkingDay = lastDay;
        this.exitReason = reason;
    }

    public boolean hasAadhaarOnFile() {
        return aadhaar != null && !aadhaar.isBlank();
    }

    /** The minimum age this system will employ anyone at. */
    public static final int MINIMUM_WORKING_AGE = 18;
}
