package com.hrsolution.content.repository;

import com.hrsolution.content.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findByPublishedTrueOrderByDisplayOrderAscTitleAsc();

    List<ServiceOffering> findAllByOrderByDisplayOrderAscTitleAsc();

    /**
     * Public lookup by slug, published only - an unpublished draft must 404 for
     * an anonymous visitor rather than render a half-written page.
     */
    Optional<ServiceOffering> findBySlugAndPublishedTrue(String slug);

    Optional<ServiceOffering> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
