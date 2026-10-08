package com.hrsolution.content.mapper;

import com.hrsolution.common.web.PublicFileUrls;
import com.hrsolution.content.dto.ContentDtos;
import com.hrsolution.content.entity.Industry;
import com.hrsolution.content.entity.ServiceOffering;
import com.hrsolution.content.entity.Testimonial;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Entity to DTO mapping for the marketing content.
 *
 * <p>Public responses expose a ready-to-use {@code ...Url} built from the stored
 * key, so the site never has to know how file routes are shaped. Admin
 * responses expose the raw key instead, because that is what the upload and
 * delete endpoints work with.
 *
 * <p>{@link PublicFileUrls} is called statically via {@code imports} rather than
 * through a default method. MapStruct treats a non-private
 * {@code String f(String)} method on a mapper as a candidate conversion for
 * EVERY String property, which silently prefixed every field in these DTOs with
 * the file URL. See docs/decisions.md.
 */
@Mapper(imports = PublicFileUrls.class)
public interface ContentMapper {

    // ---------------- Services ----------------

    @Mapping(target = "heroImageUrl", expression = "java(PublicFileUrls.of(entity.getHeroImagePath()))")
    @Mapping(target = "metaTitle", expression = "java(entity.effectiveMetaTitle())")
    @Mapping(target = "metaDescription", expression = "java(entity.effectiveMetaDescription())")
    ContentDtos.PublicServiceResponse toPublicResponse(ServiceOffering entity);

    ContentDtos.ServiceResponse toResponse(ServiceOffering entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "published", ignore = true)
    // Managed by its own upload endpoint, so saving the text cannot blank the image.
    @Mapping(target = "heroImagePath", ignore = true)
    void applyRequest(ContentDtos.ServiceRequest request, @MappingTarget ServiceOffering entity);

    // ---------------- Industries ----------------

    ContentDtos.PublicIndustryResponse toPublicResponse(Industry entity);

    ContentDtos.IndustryResponse toResponse(Industry entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "published", ignore = true)
    void applyRequest(ContentDtos.IndustryRequest request, @MappingTarget Industry entity);

    // ---------------- Testimonials ----------------

    @Mapping(target = "attribution", expression = "java(entity.attribution())")
    @Mapping(target = "logoUrl", expression = "java(PublicFileUrls.of(entity.getLogoPath()))")
    @Mapping(target = "photoUrl", expression = "java(PublicFileUrls.of(entity.getPhotoPath()))")
    ContentDtos.PublicTestimonialResponse toPublicResponse(Testimonial entity);

    @Mapping(target = "attribution", expression = "java(entity.attribution())")
    ContentDtos.TestimonialResponse toResponse(Testimonial entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "published", ignore = true)
    @Mapping(target = "logoPath", ignore = true)
    @Mapping(target = "photoPath", ignore = true)
    void applyRequest(ContentDtos.TestimonialRequest request, @MappingTarget Testimonial entity);
}
