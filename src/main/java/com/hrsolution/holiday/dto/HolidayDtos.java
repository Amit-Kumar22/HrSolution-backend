package com.hrsolution.holiday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class HolidayDtos {
    private HolidayDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 150) String name,
            @NotNull LocalDate holidayDate,
            @Size(max = 10) String state,
            boolean mandatory,
            @Size(max = 500) String notes) {
    }

    public record Response(
            Long id,
            String name,
            LocalDate holidayDate,
            String state,
            boolean mandatory,
            String notes) {
    }
}
