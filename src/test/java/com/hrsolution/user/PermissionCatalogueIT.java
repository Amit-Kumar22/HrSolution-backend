package com.hrsolution.user;

import com.hrsolution.common.security.Permissions;
import com.hrsolution.support.AbstractIntegrationTest;
import com.hrsolution.user.entity.Permission;
import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.repository.PermissionRepository;
import com.hrsolution.user.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the RBAC seed data against drift.
 *
 * <p>Permission names exist in two places that cannot reference each other: the
 * {@code INSERT} statements in Flyway migration {@code V2}, and the constants in
 * {@link Permissions} used by {@code @PreAuthorize}. A mismatch between them
 * does not fail to compile and does not throw at runtime - it just means an
 * annotation nobody can ever satisfy, or a seeded permission that grants
 * nothing. Either way the symptom is "that button does nothing for anyone",
 * discovered weeks later.
 *
 * <p>These tests make that mismatch a build failure.
 */
class PermissionCatalogueIT extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("the seeded permissions and the Permissions constants are identical")
    void permissionCatalogueMatchesConstants() {
        Set<String> inDatabase = new TreeSet<>();
        permissionRepository.findAll().forEach(permission -> inDatabase.add(permission.getName()));

        Set<String> inCode = new TreeSet<>(Permissions.ALL);

        assertThat(inDatabase)
                .as("permissions seeded by V2 but absent from Permissions.java "
                        + "(they would grant nothing, since no annotation checks them)")
                .containsExactlyElementsOf(inCode);

        assertThat(inCode)
                .as("constants in Permissions.java with no seeded row "
                        + "(any @PreAuthorize using them can never be satisfied)")
                .containsExactlyElementsOf(inDatabase);
    }

    @Test
    @DisplayName("all nine roles are seeded")
    void allRolesSeeded() {
        Set<String> seeded = new TreeSet<>();
        roleRepository.findAll().forEach(role -> seeded.add(role.getName()));

        Set<String> expected = new TreeSet<>();
        for (RoleName roleName : RoleName.values()) {
            expected.add(roleName.name());
        }

        assertThat(seeded).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("SUPER_ADMIN holds every permission")
    void superAdminHoldsEverything() {
        Role superAdmin = role(RoleName.SUPER_ADMIN);

        // The role that repairs every other role's permissions has to hold them
        // all, or the system could become unadministrable.
        assertThat(permissionNames(superAdmin)).containsExactlyElementsOf(new TreeSet<>(Permissions.ALL));
    }

    @Test
    @DisplayName("ADMIN holds everything except system settings and role administration")
    void adminIsDeliberatelyLimited() {
        Set<String> adminPermissions = permissionNames(role(RoleName.ADMIN));

        // The separation that makes SUPER_ADMIN meaningful: an ADMIN runs the
        // business but cannot change statutory configuration or grant itself
        // more permissions.
        assertThat(adminPermissions).doesNotContain(
                Permissions.SETTINGS_MANAGE, Permissions.ROLE_MANAGE);
        assertThat(adminPermissions).contains(
                Permissions.USER_MANAGE, Permissions.CLIENT_APPROVE,
                Permissions.PAYROLL_PROCESS, Permissions.INVOICE_MANAGE,
                Permissions.AUDIT_VIEW);
    }

    @Test
    @DisplayName("every role holds at least one permission")
    void noRoleIsEmpty() {
        for (RoleName roleName : RoleName.values()) {
            assertThat(permissionNames(role(roleName)))
                    .as("role %s grants nothing, so it would be useless to assign", roleName)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("external roles cannot reach payroll, billing or administration")
    void externalRolesAreContained() {
        Set<String> dangerous = Set.of(
                Permissions.PAYROLL_PROCESS, Permissions.PAYROLL_READ,
                Permissions.INVOICE_MANAGE, Permissions.PAYMENT_MANAGE,
                Permissions.USER_MANAGE, Permissions.ROLE_MANAGE,
                Permissions.SETTINGS_MANAGE, Permissions.AUDIT_VIEW,
                Permissions.WORKER_SENSITIVE_READ);

        for (RoleName roleName : new RoleName[]{RoleName.CLIENT, RoleName.WORKER, RoleName.CANDIDATE}) {
            Set<String> held = permissionNames(role(roleName));
            for (String permission : dangerous) {
                assertThat(held)
                        .as("%s must not hold %s - it is an account held by someone "
                                + "outside the company", roleName, permission)
                        .doesNotContain(permission);
            }
        }
    }

    @Test
    @DisplayName("WORKER and CANDIDATE hold only self-service permissions")
    void selfServiceRolesAreSelfServiceOnly() {
        // A worker reading another worker's payslip would be a data breach, so
        // these roles get nothing but SELF_* - plus JOB_READ for a candidate,
        // since job adverts are public anyway.
        assertThat(permissionNames(role(RoleName.WORKER)))
                .allSatisfy(permission -> assertThat(permission).startsWith("SELF_"));

        assertThat(permissionNames(role(RoleName.CANDIDATE)))
                .allSatisfy(permission -> assertThat(permission)
                        .matches(name -> name.startsWith("SELF_") || name.equals(Permissions.JOB_READ)));
    }

    @Test
    @DisplayName("SITE_SUPERVISOR can mark attendance but not approve or lock it")
    void supervisorCannotApproveOwnWork() {
        Set<String> held = permissionNames(role(RoleName.SITE_SUPERVISOR));

        assertThat(held).contains(Permissions.ATTENDANCE_MARK);
        // Separation of duties: whoever records the attendance must not be the
        // one who signs it off, since the sheet feeds payroll and billing.
        assertThat(held).doesNotContain(Permissions.ATTENDANCE_APPROVE);
    }

    @Test
    @DisplayName("ACCOUNTS can process payroll but not deploy workers or edit jobs")
    void accountsIsScopedToFinance() {
        Set<String> held = permissionNames(role(RoleName.ACCOUNTS));

        assertThat(held).contains(
                Permissions.PAYROLL_PROCESS, Permissions.INVOICE_MANAGE,
                Permissions.PAYMENT_MANAGE, Permissions.COMPLIANCE_MANAGE);
        assertThat(held).doesNotContain(
                Permissions.DEPLOYMENT_MANAGE, Permissions.JOB_MANAGE,
                Permissions.USER_MANAGE);
    }

    @Test
    @DisplayName("all nine roles are marked as system roles, so none can be deleted")
    void seededRolesAreSystemRoles() {
        for (RoleName roleName : RoleName.values()) {
            assertThat(role(roleName).isSystemRole())
                    .as("%s is referenced by name in application logic", roleName)
                    .isTrue();
        }
    }

    // ------------------------------------------------------------------

    private Role role(RoleName roleName) {
        return roleRepository.findWithPermissionsByName(roleName.name())
                .orElseThrow(() -> new AssertionError(
                        "Role " + roleName + " was not seeded by migration V2"));
    }

    private Set<String> permissionNames(Role role) {
        Set<String> names = new TreeSet<>();
        role.getPermissions().forEach((Permission permission) -> names.add(permission.getName()));
        return names;
    }
}
