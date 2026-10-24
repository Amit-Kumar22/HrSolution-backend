package com.hrsolution.worker.entity;

import com.hrsolution.common.crypto.EncryptedString;
import com.hrsolution.common.crypto.SensitiveValues;
import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Where a worker's wages are paid.
 *
 * <p>{@link #accountNumber} is encrypted, with a blind index for the
 * "is this account already registered" check. The IFSC is not: it identifies a
 * bank branch, is published by the RBI, and is useless without the account
 * number.
 *
 * <p>{@link #accountHolderName} exists because Phase 8's bank transfer file is
 * rejected when the name does not match what the bank holds, and that is not
 * always the name on the worker record - married names, initials, and
 * transliteration all differ.
 *
 * <p>Exactly one account per worker may be {@link #primaryAccount}, enforced in
 * {@code WorkerBankService}: MySQL has no partial unique index, so "unique
 * where primary is true" cannot be a constraint.
 */
@Entity
@Table(name = "worker_bank_details")
@Getter
@Setter
public class WorkerBankDetail extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Convert(converter = EncryptedString.class)
    @Column(name = "account_number_encrypted", nullable = false, length = 255)
    private String accountNumber;

    /** HMAC blind index of {@link #accountNumber}. Maintained by the service. */
    @Column(name = "account_number_hash", nullable = false, length = 64)
    private String accountNumberHash;

    @Column(name = "ifsc", nullable = false, length = 11)
    private String ifsc;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "branch", length = 120)
    private String branch;

    /** The name as the bank holds it - see the class note. */
    @Column(name = "account_holder_name", length = 160)
    private String accountHolderName;

    @Column(name = "primary_account", nullable = false)
    private boolean primaryAccount = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** {@code XXXXXXXX9012} - all a payslip or a staff screen needs. */
    public String maskedAccountNumber() {
        return SensitiveValues.maskAccountNumber(accountNumber);
    }

    /** "HDFC Bank, Bhosari — XXXXXXXX9012", for a bank-details list. */
    public String displayLabel() {
        String bank = bankName == null ? "Bank" : bankName;
        return branch == null || branch.isBlank()
                ? "%s — %s".formatted(bank, maskedAccountNumber())
                : "%s, %s — %s".formatted(bank, branch, maskedAccountNumber());
    }

    /** Whether this account can actually be paid into. */
    public boolean isPayable() {
        return active && !isDeleted() && accountNumber != null && ifsc != null;
    }
}
