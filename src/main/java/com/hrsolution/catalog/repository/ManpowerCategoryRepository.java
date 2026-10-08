package com.hrsolution.catalog.repository;

import com.hrsolution.catalog.entity.ManpowerCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManpowerCategoryRepository extends JpaRepository<ManpowerCategory, Long> {

    /** Active categories only - what the public enquiry dropdown should offer. */
    List<ManpowerCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    /** Every category, including deactivated ones, for the admin screen. */
    List<ManpowerCategory> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<ManpowerCategory> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);
}
