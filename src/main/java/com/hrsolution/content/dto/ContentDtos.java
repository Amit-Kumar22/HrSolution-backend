package com.hrsolution.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the editable marketing content. */
public final class ContentDtos {

    private ContentDtos() {
    }

    /** Slugs appear in URLs, so the shape is constrained rather than sanitised. */
    private static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    private static final String SLUG_MESSAGE =
            "must be lower-case words separated by single hyphens, e.g. manpower-supply";

    // ---------------- Services ----------------

    @Schema(description = "A service offering, as shown on the public site")
    public record PublicServiceResponse(
            @Schema(example = "manpower-supply") String slug,
            String title,
            String summary,
            String description,
            String icon,
            @Schema(description = "Public URL of the hero image, if one is set")
            String heroImageUrl,
            @Schema(description = "SEO title, falling back to the page title")
            String metaTitle,
            @Schema(description = "SEO description, falling back to the summary")
            String metaDescription,
            int displayOrder) {
    }

    @Schema(description = "A service offering, including unpublished drafts")
    public record ServiceResponse(
            Long id,
            String slug,
            String title,
            String summary,
            String description,
            String icon,
            String heroImagePath,
            String metaTitle,
            String metaDescription,
            int displayOrder,
            boolean published) {
    }

    @Schema(description = "Create or replace a service offering")
    public record ServiceRequest(

            @NotBlank(message = "Slug is required")
            @Size(max = 120)
            @Pattern(regexp = SLUG_PATTERN, message = SLUG_MESSAGE)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "URL segment. Avoid changing it once published - it breaks "
                            + "inbound links and discards search ranking.",
                    example = "manpower-supply")
            String slug,

            @NotBlank(message = "Title is required")
            @Size(max = 160)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Manpower Supply")
            String title,

            @NotBlank(message = "Summary is required")
            @Size(max = 400)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Card text on the services index")
            String summary,

            @Size(max = 20000)
            @Schema(description = "Full page body")
            String description,

            @Size(max = 60)
            @Schema(description = "Icon name for the site to render", example = "users")
            String icon,

            @Size(max = 160)
            String metaTitle,

            @Size(max = 320)
            String metaDescription,

            int displayOrder,

            @Schema(description = "Unpublished by default, so a draft cannot appear by accident",
                    defaultValue = "false")
            Boolean published) {
    }

    // ---------------- Industries ----------------

    @Schema(description = "An industry served, as shown on the public site")
    public record PublicIndustryResponse(
            String slug,
            String name,
            String description,
            String icon,
            int displayOrder) {
    }

    @Schema(description = "An industry served, including unpublished drafts")
    public record IndustryResponse(
            Long id,
            String slug,
            String name,
            String description,
            String icon,
            int displayOrder,
            boolean published) {
    }

    @Schema(description = "Create or replace an industry")
    public record IndustryRequest(

            @NotBlank(message = "Slug is required")
            @Size(max = 120)
            @Pattern(regexp = SLUG_PATTERN, message = SLUG_MESSAGE)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "manufacturing")
            String slug,

            @NotBlank(message = "Name is required")
            @Size(max = 120)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Manufacturing & Factories")
            String name,

            @Size(max = 600)
            String description,

            @Size(max = 60)
            String icon,

            int displayOrder,

            Boolean published) {
    }

    // ---------------- Testimonials ----------------

    @Schema(description = "A published client testimonial")
    public record PublicTestimonialResponse(
            Long id,
            String clientName,
            String clientCompany,
            String designation,
            @Schema(description = "Pre-formatted as \"Name, Designation, Company\"")
            String attribution,
            String content,
            Integer rating,
            @Schema(description = "Public URL of the client logo, if one is set")
            String logoUrl,
            @Schema(description = "Public URL of the person's photo, if one is set")
            String photoUrl) {
    }

    @Schema(description = "A testimonial, including unpublished ones")
    public record TestimonialResponse(
            Long id,
            String clientName,
            String clientCompany,
            String designation,
            String attribution,
            String content,
            Integer rating,
            String logoPath,
            String photoPath,
            int displayOrder,
            boolean published) {
    }

    @Schema(description = "Create or replace a testimonial")
    public record TestimonialRequest(

            @NotBlank(message = "Client name is required")
            @Size(max = 120)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rajesh Kulkarni")
            String clientName,

            @Size(max = 200)
            @Schema(example = "Bharat Textiles Private Limited")
            String clientCompany,

            @Size(max = 120)
            @Schema(example = "Plant Head")
            String designation,

            @NotBlank(message = "The testimonial text is required")
            @Size(max = 1500)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String content,

            @Min(value = 1, message = "must be between 1 and 5")
            @Max(value = 5, message = "must be between 1 and 5")
            @Schema(description = "Optional 1-5 star rating")
            Integer rating,

            int displayOrder,

            @Schema(description = "Quoting a named person is a reputational commitment, "
                    + "so this is false until someone approves it",
                    defaultValue = "false")
            Boolean published) {
    }
}
