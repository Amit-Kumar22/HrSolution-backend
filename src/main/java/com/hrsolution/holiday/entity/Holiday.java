package com.hrsolution.holiday.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "holidays",
        uniqueConstraints = @UniqueConstraint(name = "uk_holidays_date_state",
                columnNames = {"holiday_date", "state"}))
@Getter
@Setter
public class Holiday extends SoftDeletableEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 10)
    private String state;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(length = 500)
    private String notes;
}
