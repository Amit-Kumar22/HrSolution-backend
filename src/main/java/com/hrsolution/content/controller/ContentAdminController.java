package com.hrsolution.content.controller;

import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.content.dto.ContentDtos;
import com.hrsolution.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Editing the marketing site's content: services, industries and testimonials.
 *
 * <p>All of it behind {@code CONTENT_MANAGE}, which SUPER_ADMIN and ADMIN hold.
 * Reading includes unpublished drafts, which is the difference from
 * {@link PublicSiteController}.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Website content", description = "Services, industries and testimonials")
public class ContentAdminController {

    private final ContentService contentService;

    // ==================================================================
    // Services
    // ==================================================================

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "List all services, including unpublished drafts")
    public List<ContentDtos.ServiceResponse> listServices() {
        return contentService.listServices();
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Get one service")
    public ContentDtos.ServiceResponse getService(@PathVariable Long id) {
        return contentService.getService(id);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Create a service page",
            description = "Unpublished unless you pass published=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409", description = "Slug already used (DUPLICATE_RESOURCE)")
    })
    public ContentDtos.ServiceResponse createService(
            @Valid @RequestBody ContentDtos.ServiceRequest request) {
        return contentService.createService(request);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Update a service page",
            description = "The slug of a PUBLISHED page cannot be changed - it would break every "
                    + "inbound link and discard the page's search ranking. Unpublish it first if "
                    + "you really mean to rename it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "422",
                    description = "Attempted to change a published slug (BUSINESS_RULE_VIOLATION)")
    })
    public ContentDtos.ServiceResponse updateService(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.ServiceRequest request) {
        return contentService.updateService(id, request);
    }

    @PatchMapping("/services/{id}/publish")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Publish or unpublish a service page")
    public ContentDtos.ServiceResponse publishService(
            @PathVariable Long id,
            @Parameter(description = "true to publish, false to take it down")
            @RequestParam(defaultValue = "true") boolean published) {
        return contentService.setServicePublished(id, published);
    }

    @PostMapping(value = "/services/{id}/hero-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Upload a hero image",
            description = "PNG, JPEG or GIF, up to 2 MB. The type is detected from the file's "
                    + "content, so renaming a file does not get it past validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uploaded"),
            @ApiResponse(responseCode = "400",
                    description = "Not an image, or too large (VALIDATION_FAILED)")
    })
    public ContentDtos.ServiceResponse uploadServiceHeroImage(
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return contentService.uploadServiceHeroImage(id, file);
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Delete a service page",
            description = "Must be unpublished first, so a live page cannot disappear from under "
                    + "traffic by accident. Page content is not a business record, so this is a "
                    + "real delete rather than a soft one.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long id) {
        contentService.deleteService(id);
    }

    // ==================================================================
    // Industries
    // ==================================================================

    @GetMapping("/industries")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "List all industries, including unpublished")
    public List<ContentDtos.IndustryResponse> listIndustries() {
        return contentService.listIndustries();
    }

    @PostMapping("/industries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Create an industry")
    public ContentDtos.IndustryResponse createIndustry(
            @Valid @RequestBody ContentDtos.IndustryRequest request) {
        return contentService.createIndustry(request);
    }

    @PutMapping("/industries/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Update an industry")
    public ContentDtos.IndustryResponse updateIndustry(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.IndustryRequest request) {
        return contentService.updateIndustry(id, request);
    }

    @DeleteMapping("/industries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Delete an industry")
    public void deleteIndustry(@PathVariable Long id) {
        contentService.deleteIndustry(id);
    }

    // ==================================================================
    // Testimonials
    // ==================================================================

    @GetMapping("/testimonials")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "List all testimonials, including unapproved")
    public PageResponse<ContentDtos.TestimonialResponse> listTestimonials(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(contentService.listTestimonials(pageable));
    }

    @PostMapping("/testimonials")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Create a testimonial",
            description = "Unpublished by default. Quoting a named person at a named client is a "
                    + "reputational commitment, so publishing is a separate, deliberate step.")
    public ContentDtos.TestimonialResponse createTestimonial(
            @Valid @RequestBody ContentDtos.TestimonialRequest request) {
        return contentService.createTestimonial(request);
    }

    @PutMapping("/testimonials/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Update a testimonial")
    public ContentDtos.TestimonialResponse updateTestimonial(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.TestimonialRequest request) {
        return contentService.updateTestimonial(id, request);
    }

    @PatchMapping("/testimonials/{id}/publish")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Publish or unpublish a testimonial")
    public ContentDtos.TestimonialResponse publishTestimonial(
            @PathVariable Long id, @RequestParam(defaultValue = "true") boolean published) {
        return contentService.setTestimonialPublished(id, published);
    }

    @PostMapping(value = "/testimonials/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Upload a client logo or a photo of the person",
            description = "PNG, JPEG or GIF, up to 2 MB, validated by content.")
    public ContentDtos.TestimonialResponse uploadTestimonialImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "true for the company logo, false for the person's photo")
            @RequestParam(defaultValue = "true") boolean logo) {
        return contentService.uploadTestimonialImage(id, file, logo);
    }

    @DeleteMapping("/testimonials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Delete a testimonial")
    public void deleteTestimonial(@PathVariable Long id) {
        contentService.deleteTestimonial(id);
    }
}
