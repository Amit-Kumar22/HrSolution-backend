package com.hrsolution.user.repository;

import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsById(Long id);

    /** All roles with permissions fetched, for the role administration screen. */
    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAllByOrderByNameAsc();

    Set<Role> findByNameIn(Set<String> names);

    default Optional<Role> findByRoleName(RoleName roleName) {
        return findByName(roleName.name());
    }
}
