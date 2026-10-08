package com.hrsolution.content.repository;

import com.hrsolution.content.entity.Industry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IndustryRepository extends JpaRepository<Industry, Long> {

    List<Industry> findByPublishedTrueOrderByDisplayOrderAscNameAsc();

    List<Industry> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<Industry> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
