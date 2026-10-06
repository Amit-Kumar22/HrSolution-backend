package com.hrsolution.user.repository;

import com.hrsolution.user.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscNameAsc();

    Set<Permission> findByNameIn(Set<String> names);
}
