package com.hrsolution.content.repository;

import com.hrsolution.content.entity.Testimonial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    List<Testimonial> findByPublishedTrueOrderByDisplayOrderAscCreatedAtDesc();

    Page<Testimonial> findAllByOrderByDisplayOrderAscCreatedAtDesc(Pageable pageable);
}
