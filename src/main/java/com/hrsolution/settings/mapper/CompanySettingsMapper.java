package com.hrsolution.settings.mapper;

import com.hrsolution.common.web.PublicFileUrls;
import com.hrsolution.settings.dto.CompanySettingsResponse;
import com.hrsolution.settings.dto.PublicCompanyProfileResponse;
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
/*
 * imports makes PublicFileUrls resolvable inside the generated class - a
 * MapStruct expression is pasted verbatim and does not inherit this file's
 * imports.
 *
 * It is referenced statically rather than through a default method on purpose.
 * MapStruct treats any non-private method on a mapper as a candidate conversion
 * for its signature, so a `String publicUrl(String)` helper here got applied to
 * every single String property - prefixing the company name, the address and
 * everything else with the file URL. See docs/decisions.md.
 */
@Mapper(imports = PublicFileUrls.class)
public interface CompanySettingsMapper {

    CompanySettingsResponse toResponse(CompanySettings entity);

    /**
     * The narrow view served to the public marketing site.
     *
     * <p>Note the fields that are absent because the target record simply has no
     * component for them: bank account, IFSC, TAN, and the PF/ESI/PT codes. That
     * is the mechanism, not an oversight - with
     * {@code unmappedTargetPolicy=ERROR} the compiler checks that every field of
     * the public record IS mapped, while anything the record omits cannot be
     * published however the entity changes.
     */
    @Mapping(target = "logoUrl", expression = "java(PublicFileUrls.of(entity.getLogoPath()))")
    PublicCompanyProfileResponse toPublicProfile(CompanySettings entity);

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
