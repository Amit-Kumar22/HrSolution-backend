package com.hrsolution.user.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A named bundle of {@link Permission}s.
 *
 * <p>{@link #systemRole} marks the nine seeded roles, which the admin API
 * refuses to delete or rename because application logic refers to them by name.
 * Their permission sets, however, remain editable by a SUPER_ADMIN.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole = true;

    /**
     * Lazy: the vast majority of queries that touch a role do not need its
     * permissions. The login path, which does, fetches them explicitly with an
     * entity graph so that it costs one query rather than N.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    public RoleName asRoleName() {
        return RoleName.valueOf(name);
    }

    public void replacePermissions(Set<Permission> replacements) {
        this.permissions.clear();
        this.permissions.addAll(replacements);
    }
}
