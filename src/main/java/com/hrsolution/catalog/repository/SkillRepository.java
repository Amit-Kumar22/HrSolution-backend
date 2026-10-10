package com.hrsolution.catalog.repository;

import com.hrsolution.catalog.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByActiveTrueOrderByNameAsc();

    List<Skill> findAllByOrderByNameAsc();

    Set<Skill> findByIdIn(Set<Long> ids);

    boolean existsByNameIgnoreCase(String name);
}
