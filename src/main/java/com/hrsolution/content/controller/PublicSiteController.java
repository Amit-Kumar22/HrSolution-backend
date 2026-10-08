package com.hrsolution.content.controller;

import com.hrsolution.catalog.dto.ManpowerCategoryDtos;
import com.hrsolution.catalog.service.ManpowerCategoryService;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.content.dto.ContentDtos;
import com.hrsolution.content.service.ContentService;
import com.hrsolution.enquiry.dto.ContactMessageDtos;
import com.hrsolution.enquiry.dto.EnquiryDtos;
import com.hrsolution.enquiry.service.ContactMessageService;
import com.hrsolution.enquiry.service.EnquiryService;
import com.hrsolution.settings.dto.PublicCompanyProfileResponse;
import com.hrsolution.settings.service.CompanySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Everything the public marketing website needs. No authentication.
 *
 * <p>Gathered into one controller on purpose. Every endpoint here shares the
 * same unusual properties - anonymous, cached, rate limited, and returning only
 * deliberately-trimmed data - and keeping them together means the question
 * "what can the internet see?" is answered by reading one file rather than
 * auditing a dozen.
 *
 * <p>Note what the read endpoints do <em>not</em> expose: unpublished drafts,
 * internal notes, and - on the company profile - the bank account and statutory
 * registration codes. See {@link PublicCompanyProfileResponse} for why.
 *
 * <p>The two form submissions are protected by per-IP rate limiting in
 * {@code RateLimitFilter} and by the honeypot and timing checks in
 * {@code SpamGuard}. Neither ever rejects a submission outright; a suspected one
 * is stored and flagged, because a false positive here loses a real sales lead.
 */
@RestController
@RequestMapping(ApiPaths.PUBLIC_V1)
@RequiredArgsConstructor
@Tag(name = "Public website",
        description = "Unauthenticated endpoints for the marketing site: content, company "
                + "details, and the enquiry and contact forms")
public class PublicSiteController {

    private final ContentService contentService;
    private final ManpowerCategoryService categoryService;
    private final CompanySettingsService companySettingsService;
    private final EnquiryService enquiryService;
    private final ContactMessageService contactMessageService;

    // ==================================================================
    // Company details
    // ==================================================================

    @GetMapping("/company-profile")
    @Operation(summary = "Company details for the site header, footer and contact page",
            description = "A deliberately narrow view. Bank details, TAN and the PF/ESI/PT "
                    + "registration codes are omitted - they belong on invoices and statutory "
                    + "returns, not on a public endpoint. GSTIN, PAN and CIN are included, since "
                    + "Indian companies are required to display them.")
    @ApiResponse(responseCode = "200", description = "The company profile")
    public PublicCompanyProfileResponse companyProfile() {
        return companySettingsService.getPublicProfile();
    }

    // ==================================================================
    // Content
    // ==================================================================

    @GetMapping("/services")
    @Operation(summary = "List published services",
            description = "Ordered by display order. Unpublished drafts are never included.")
    public List<ContentDtos.PublicServiceResponse> services() {
        return contentService.publishedServices();
    }

    @GetMapping("/services/{slug}")
    @Operation(summary = "Get one published service by slug",
            description = "An unpublished draft returns 404 rather than rendering, so a "
                    + "work-in-progress page cannot be read by guessing its URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The service page"),
            @ApiResponse(responseCode = "404",
                    description = "No published service at that slug (RESOURCE_NOT_FOUND)")
    })
    public ContentDtos.PublicServiceResponse service(@PathVariable String slug) {
        return contentService.publishedServiceBySlug(slug);
    }

    @GetMapping("/industries")
    @Operation(summary = "List the industries served")
    public List<ContentDtos.PublicIndustryResponse> industries() {
        return contentService.publishedIndustries();
    }

    @GetMapping("/testimonials")
    @Operation(summary = "List published client testimonials")
    public List<ContentDtos.PublicTestimonialResponse> testimonials() {
        return contentService.publishedTestimonials();
    }

    @GetMapping("/manpower-categories")
    @Operation(summary = "Categories for the enquiry form dropdown",
            description = "Active categories only. The enquiry form also accepts free text, so a "
                    + "requirement that does not fit this list is still submittable.")
    public List<ManpowerCategoryDtos.PublicResponse> manpowerCategories() {
        return categoryService.listActiveForPublic();
    }

    // ==================================================================
    // Forms
    // ==================================================================

    @PostMapping("/enquiries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a manpower requirement enquiry",
            description = """
                    Only company name, contact person and phone are required - this is the top of \
                    the sales funnel, and rejecting a lead over a missing start date costs real \
                    business.

                    Spam protection: send the hidden `website` honeypot field empty, and \
                    optionally `formRenderedAt` as epoch millis so a submission too fast to be \
                    human can be spotted. A suspected submission is stored and flagged rather \
                    than refused, and the response is identical either way.

                    Rate limited per IP.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Enquiry received. Returns a reference such as ENQ-000042."),
            @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "429",
                    description = "Too many submissions from this IP (RATE_LIMIT_EXCEEDED)")
    })
    public EnquiryDtos.SubmitResponse submitEnquiry(
            @Valid @RequestBody EnquiryDtos.SubmitRequest request,
            HttpServletRequest httpRequest) {
        return enquiryService.submit(request, RequestContext.from(httpRequest));
    }

    @PostMapping("/contact-messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit the Contact Us form",
            description = "Same honeypot and timing protection as the enquiry form. The "
                    + "acknowledgement does not reveal whether the submission was flagged.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message received"),
            @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "429", description = "Too many submissions (RATE_LIMIT_EXCEEDED)")
    })
    public EnquiryDtos.SubmitResponse submitContactMessage(
            @Valid @RequestBody ContactMessageDtos.SubmitRequest request,
            HttpServletRequest httpRequest) {
        contactMessageService.submit(request, RequestContext.from(httpRequest));
        // No id or reference: a contact message has no pipeline to track, and
        // handing out ids on an anonymous endpoint invites enumeration.
        return new EnquiryDtos.SubmitResponse(null,
                "Thank you for getting in touch. We will reply to the email address you provided.");
    }
}
