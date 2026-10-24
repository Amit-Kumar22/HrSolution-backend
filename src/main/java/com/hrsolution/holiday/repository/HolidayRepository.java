package com.hrsolution.holiday.repository;

import com.hrsolution.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    @Query("select h from Holiday h where h.deleted = false and h.holidayDate between :from and :to "
            + "and (h.state is null or h.state = :state)")
    List<Holiday> findApplicable(@Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("state") String state);
}
