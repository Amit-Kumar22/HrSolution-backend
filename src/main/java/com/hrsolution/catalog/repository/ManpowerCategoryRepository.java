package com.hrsolution.catalog.repository;

import com.hrsolution.catalog.entity.ManpowerCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManpowerCategoryRepository extends JpaRepository<ManpowerCategory, Long> {

    /** Active categories only - what the public enquiry dropdown should offer. */
    List<ManpowerCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    /** Every category, including deactivated ones, for the admin screen. */
    List<ManpowerCategory> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<ManpowerCategory> findByCode(String code);

    /**
     * Fetches a category with its skills in one query.
     *
     * <p>Needed because {@code skills} is lazy - the category list is read on
     * nearly every screen and almost never wants them, so only the screens that
     * do pay for the join.
     */
    @EntityGraph(attributePaths = {"skills"})
    Optional<ManpowerCategory> findWithSkillsById(Long id);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);
}
