package com.hrsolution.settings.mapper;

import com.hrsolution.settings.dto.CompanySettingsResponse;
import com.hrsolution.settings.dto.UpdateCompanySettingsRequest;
import com.hrsolution.settings.entity.CompanySettings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Entity to DTO mapping for the company profile.
 *
 * <p>MapStruct generates the implementation at compile time into
 * {@code target/generated-sources/annotations}; read that file if a mapping ever
 * behaves unexpectedly. The {@code componentModel=spring} setting comes from a
 * compiler argument in {@code pom.xml}, so the generated class is a Spring bean
 * and can simply be injected.
 *
 * <p><strong>The ignore list on {@link #applyUpdate} is required.</strong> The
 * build runs MapStruct with {@code unmappedTargetPolicy=ERROR}, so every
 * writable property on the target must be either mapped or explicitly ignored.
 * That is a deliberate trade: it costs these seven lines, and in exchange adding
 * a field to the entity without adding it to the DTO becomes a compile error
 * rather than a field that silently never saves. Copy this pattern for every
 * update mapper.
 */
@Mapper
public interface CompanySettingsMapper {

    CompanySettingsResponse toResponse(CompanySettings entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    // Maintained by the logo upload endpoint, not by this form.
    @Mapping(target = "logoPath", ignore = true)
    void applyUpdate(UpdateCompanySettingsRequest request, @MappingTarget CompanySettings entity);
}
