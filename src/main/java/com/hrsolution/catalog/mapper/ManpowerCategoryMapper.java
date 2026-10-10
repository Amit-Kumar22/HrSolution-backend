package com.hrsolution.catalog.mapper;

import com.hrsolution.catalog.dto.ManpowerCategoryDtos;
import com.hrsolution.catalog.entity.ManpowerCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface ManpowerCategoryMapper {

    @Mapping(target = "skillLevelLabel", expression = "java(entity.getSkillLevel().getDisplayName())")
    ManpowerCategoryDtos.Response toResponse(ManpowerCategory entity);

    @Mapping(target = "skillLevelLabel", expression = "java(entity.getSkillLevel().getDisplayName())")
    ManpowerCategoryDtos.PublicResponse toPublicResponse(ManpowerCategory entity);

    // Audit columns, id and version are managed by the framework, never by a
    // request body. See CompanySettingsMapper for why the list is explicit.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    // Managed by PUT /manpower-categories/{id}/skills, so editing a category's
    // name cannot silently clear the skills it requires.
    @Mapping(target = "skills", ignore = true)
    void applyRequest(ManpowerCategoryDtos.Request request, @MappingTarget ManpowerCategory entity);
}
