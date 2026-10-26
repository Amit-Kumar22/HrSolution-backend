package com.hrsolution.compliance.repository;

import com.hrsolution.compliance.entity.StatutoryChallan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StatutoryChallanRepository extends JpaRepository<StatutoryChallan, Long>, JpaSpecificationExecutor<StatutoryChallan> {
}
