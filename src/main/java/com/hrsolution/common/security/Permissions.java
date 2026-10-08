package com.hrsolution.common.security;

import java.util.Set;

/**
 * Permission names, as compile-time constants for {@code @PreAuthorize}.
 *
 * <pre>{@code
 * @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
 * }</pre>
 *
 * <p>Annotation values must be constant expressions, which rules out an enum
 * here - hence a constants class. Authorisation is always checked on these, not
 * on role names, so the role-to-permission mapping stays editable at runtime.
 *
 * <p>These names must exactly match the rows seeded by Flyway migration
 * {@code V2}. {@code PermissionCatalogueIT} compares {@link #ALL} against the
 * database and fails the build on any drift, so a typo cannot quietly create a
 * permission that nothing grants or an annotation nobody can satisfy.
 */
public final class Permissions {

    // ---------- User and access administration ----------
    public static final String USER_READ = "USER_READ";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";

    // ---------- Clients ----------
    public static final String CLIENT_READ = "CLIENT_READ";
    public static final String CLIENT_WRITE = "CLIENT_WRITE";
    public static final String CLIENT_APPROVE = "CLIENT_APPROVE";

    // ---------- Requisitions ----------
    public static final String REQUISITION_READ = "REQUISITION_READ";
    public static final String REQUISITION_CREATE = "REQUISITION_CREATE";
    public static final String REQUISITION_APPROVE = "REQUISITION_APPROVE";

    // ---------- Recruitment ----------
    public static final String JOB_READ = "JOB_READ";
    public static final String JOB_MANAGE = "JOB_MANAGE";
    public static final String CANDIDATE_READ = "CANDIDATE_READ";
    public static final String CANDIDATE_WRITE = "CANDIDATE_WRITE";
    public static final String APPLICATION_READ = "APPLICATION_READ";
    public static final String APPLICATION_MANAGE = "APPLICATION_MANAGE";

    // ---------- Workers ----------
    public static final String WORKER_READ = "WORKER_READ";
    public static final String WORKER_WRITE = "WORKER_WRITE";
    /** Unmasked Aadhaar, PAN and bank account numbers. Granted sparingly. */
    public static final String WORKER_SENSITIVE_READ = "WORKER_SENSITIVE_READ";

    // ---------- Deployment ----------
    public static final String DEPLOYMENT_READ = "DEPLOYMENT_READ";
    public static final String DEPLOYMENT_MANAGE = "DEPLOYMENT_MANAGE";

    // ---------- Attendance ----------
    public static final String ATTENDANCE_READ = "ATTENDANCE_READ";
    public static final String ATTENDANCE_MARK = "ATTENDANCE_MARK";
    public static final String ATTENDANCE_APPROVE = "ATTENDANCE_APPROVE";
    public static final String LEAVE_APPROVE = "LEAVE_APPROVE";

    // ---------- Payroll ----------
    public static final String PAYROLL_READ = "PAYROLL_READ";
    public static final String PAYROLL_PROCESS = "PAYROLL_PROCESS";

    // ---------- Billing ----------
    public static final String INVOICE_READ = "INVOICE_READ";
    public static final String INVOICE_MANAGE = "INVOICE_MANAGE";
    public static final String PAYMENT_MANAGE = "PAYMENT_MANAGE";

    // ---------- Compliance ----------
    public static final String COMPLIANCE_READ = "COMPLIANCE_READ";
    public static final String COMPLIANCE_MANAGE = "COMPLIANCE_MANAGE";

    // ---------- Cross-cutting ----------
    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String ENQUIRY_MANAGE = "ENQUIRY_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";

    /**
     * Editable marketing content: services, industries, testimonials and the
     * manpower category master.
     *
     * <p>Separate from {@link #SETTINGS_MANAGE} because rewording a services
     * page is routine marketing work, whereas SETTINGS_MANAGE reaches statutory
     * configuration and the invoice number series.
     */
    public static final String CONTENT_MANAGE = "CONTENT_MANAGE";

    // ---------- Self-service ----------
    // Holding one of these is necessary but never sufficient: the services also
    // check that the record belongs to the caller, so a WORKER cannot read
    // another worker's payslip by holding SELF_PAYSLIP_READ.
    public static final String SELF_PROFILE_MANAGE = "SELF_PROFILE_MANAGE";
    public static final String SELF_ATTENDANCE_READ = "SELF_ATTENDANCE_READ";
    public static final String SELF_PAYSLIP_READ = "SELF_PAYSLIP_READ";
    public static final String SELF_LEAVE_MANAGE = "SELF_LEAVE_MANAGE";
    public static final String SELF_APPLICATION_MANAGE = "SELF_APPLICATION_MANAGE";

    /** Every permission the application knows about. Used by the drift test. */
    public static final Set<String> ALL = Set.of(
            USER_READ, USER_MANAGE, ROLE_MANAGE,
            CLIENT_READ, CLIENT_WRITE, CLIENT_APPROVE,
            REQUISITION_READ, REQUISITION_CREATE, REQUISITION_APPROVE,
            JOB_READ, JOB_MANAGE, CANDIDATE_READ, CANDIDATE_WRITE,
            APPLICATION_READ, APPLICATION_MANAGE,
            WORKER_READ, WORKER_WRITE, WORKER_SENSITIVE_READ,
            DEPLOYMENT_READ, DEPLOYMENT_MANAGE,
            ATTENDANCE_READ, ATTENDANCE_MARK, ATTENDANCE_APPROVE, LEAVE_APPROVE,
            PAYROLL_READ, PAYROLL_PROCESS,
            INVOICE_READ, INVOICE_MANAGE, PAYMENT_MANAGE,
            COMPLIANCE_READ, COMPLIANCE_MANAGE,
            REPORT_VIEW, ENQUIRY_MANAGE, SETTINGS_MANAGE, AUDIT_VIEW, CONTENT_MANAGE,
            SELF_PROFILE_MANAGE, SELF_ATTENDANCE_READ, SELF_PAYSLIP_READ,
            SELF_LEAVE_MANAGE, SELF_APPLICATION_MANAGE);

    private Permissions() {
    }
}
