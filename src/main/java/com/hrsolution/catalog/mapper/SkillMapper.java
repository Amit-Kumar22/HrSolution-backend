package com.hrsolution.catalog.mapper;

import com.hrsolution.catalog.dto.SkillDtos;
import com.hrsolution.catalog.entity.Skill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface SkillMapper {

    SkillDtos.Response toResponse(Skill entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    void applyRequest(SkillDtos.Request request, @MappingTarget Skill entity);
}
