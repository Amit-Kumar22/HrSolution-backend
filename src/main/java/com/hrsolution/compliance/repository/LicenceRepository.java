package com.hrsolution.compliance.repository;

import com.hrsolution.compliance.entity.Licence;
import com.hrsolution.compliance.entity.LicenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

public interface LicenceRepository extends JpaRepository<Licence, Long>, JpaSpecificationExecutor<Licence> {
    List<Licence> findByExpiryDateBetweenAndDeletedFalse(LocalDate from, LocalDate to);
    List<Licence> findByStatusAndDeletedFalse(LicenceStatus status);
}
