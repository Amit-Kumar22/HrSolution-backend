package com.hrsolution.user.entity;

/**
 * The nine seeded roles.
 *
 * <p>Roles exist to be *bundles of permissions*. Application code authorises on
 * permissions ({@code hasAuthority('PAYROLL_PROCESS')}), never on role names,
 * so that the role-to-permission mapping can be re-pointed from the admin API
 * without a code change. This enum exists for seeding, for assigning roles, and
 * for the handful of places that genuinely need to know which kind of account
 * they are dealing with - for example, that a {@link #CLIENT} login must be
 * scoped to its own client record.
 */
public enum RoleName {

    /** Company owner. Everything, including system settings and role editing. */
    SUPER_ADMIN,

    /** Operations head. All modules except system settings and role editing. */
    ADMIN,

    /** Recruitment team. */
    HR_RECRUITER,

    /** Deployment team. */
    OPERATIONS_MANAGER,

    /** Field supervisor; attendance for assigned sites only. */
    SITE_SUPERVISOR,

    /** Finance team. */
    ACCOUNTS,

    /** Client company user; own data only. */
    CLIENT,

    /** Deployed employee; own data only. */
    WORKER,

    /** Job seeker; own data only. */
    CANDIDATE;

    /** Roles whose holders are staff of the service provider, not outsiders. */
    public boolean isInternalStaff() {
        return this != CLIENT && this != WORKER && this != CANDIDATE;
    }
}
