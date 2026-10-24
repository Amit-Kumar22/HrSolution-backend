package com.hrsolution.holiday.mapper;

import com.hrsolution.holiday.dto.HolidayDtos;
import com.hrsolution.holiday.entity.Holiday;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface HolidayMapper {

    @org.mapstruct.Mapping(target = "id", ignore = true)
    @org.mapstruct.Mapping(target = "createdAt", ignore = true)
    @org.mapstruct.Mapping(target = "updatedAt", ignore = true)
    @org.mapstruct.Mapping(target = "createdBy", ignore = true)
    @org.mapstruct.Mapping(target = "updatedBy", ignore = true)
    @org.mapstruct.Mapping(target = "version", ignore = true)
    @org.mapstruct.Mapping(target = "deleted", ignore = true)
    @org.mapstruct.Mapping(target = "deletedAt", ignore = true)
    @org.mapstruct.Mapping(target = "deletedBy", ignore = true)
    Holiday toEntity(HolidayDtos.Request request);

    HolidayDtos.Response toResponse(Holiday holiday);

    @org.mapstruct.Mapping(target = "id", ignore = true)
    @org.mapstruct.Mapping(target = "createdAt", ignore = true)
    @org.mapstruct.Mapping(target = "updatedAt", ignore = true)
    @org.mapstruct.Mapping(target = "createdBy", ignore = true)
    @org.mapstruct.Mapping(target = "updatedBy", ignore = true)
    @org.mapstruct.Mapping(target = "version", ignore = true)
    @org.mapstruct.Mapping(target = "deleted", ignore = true)
    @org.mapstruct.Mapping(target = "deletedAt", ignore = true)
    @org.mapstruct.Mapping(target = "deletedBy", ignore = true)
    void apply(HolidayDtos.Request request, @MappingTarget Holiday holiday);
}
