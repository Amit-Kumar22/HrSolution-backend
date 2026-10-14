package com.hrsolution.recruitment.controller;

import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.ApplicationDtos;
import com.hrsolution.recruitment.dto.JobDtos;
import com.hrsolution.recruitment.service.ApplicationService;
import com.hrsolution.recruitment.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The public careers page. No authentication.
 *
 * <p>Kept separate from {@code PublicSiteController} because the apply endpoint
 * is the only anonymous route in the system that accepts a file upload and
 * creates two rows in two tables, and that deserves to be read in one place
 * rather than found among the marketing endpoints.
 *
 * <p><strong>Nothing here exposes which client a vacancy is for.</strong>
 * {@link JobDtos.PublicResponse} has no client or site field at all - see
 * {@code Job} for why that is structural rather than a mapping choice.
 */
@RestController
@RequestMapping(ApiPaths.PUBLIC_V1 + "/jobs")
@RequiredArgsConstructor
@Tag(name = "Public careers",
        description = "Unauthenticated endpoints for the careers page: open vacancies and the "
                + "apply form")
public class PublicCareersController {

    private final JobService jobService;
    private final ApplicationService applicationService;

    @GetMapping
    @Operation(summary = "List open vacancies",
            description = "Only adverts that are OPEN and have not passed their closing date. "
                    + "Never includes drafts, held or filled roles, and never names the client.")
    public PageResponse<JobDtos.PublicSummaryResponse> list(
            @Parameter(description = "Filter to one kind of work. Ids from "
                    + "GET /public/manpower-categories.")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Exact, case-insensitive city match", example = "Pune")
            @RequestParam(required = false) String city,

            @ParameterObject
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return jobService.listPublic(categoryId, city, pageable);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get one open vacancy by slug",
            description = "404 once the advert is withdrawn, filled or past its closing date, "
                    + "so a stale link does not show a vacancy nobody can apply to.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The vacancy"),
            @ApiResponse(responseCode = "404",
                    description = "No open vacancy at that address (RESOURCE_NOT_FOUND)")
    })
    public JobDtos.PublicResponse get(@PathVariable String slug) {
        return jobService.getPublicBySlug(slug);
    }

    /**
     * The anonymous apply form.
     *
     * <p>Multipart because the CV comes with it: asking an applicant to
     * register, sign in and then upload loses real candidates, especially for
     * operative roles filled from a phone. The JSON body travels in a part
     * named {@code application} and the file in one named {@code resume}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, path = "/{slug}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for a job without an account",
            description = """
                    Multipart with two parts:

                    * `application` - the JSON body (`PublicApplyRequest`), content type
                      `application/json`
                    * `resume` - an optional PDF, 10 MB maximum

                    Render the `website` field hidden and leave it empty, and send
                    `formRenderedAt` as epoch millis when the form mounts. A tripped
                    honeypot or an implausibly fast submission is **accepted with 201** and
                    flagged for staff review rather than rejected, so never tell the
                    submitter.

                    Rate limited per IP.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application received"),
            @ApiResponse(responseCode = "400",
                    description = "Validation failed, including missing consent (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "404",
                    description = "No open vacancy at that address (RESOURCE_NOT_FOUND)"),
            @ApiResponse(responseCode = "409",
                    description = "An application already exists for this email and job "
                            + "(DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "415",
                    description = "The resume is not a PDF, detected by content "
                            + "(UNSUPPORTED_MEDIA_TYPE)"),
            @ApiResponse(responseCode = "429",
                    description = "Too many applications from this address (RATE_LIMIT_EXCEEDED)")
    })
    public ApplicationDtos.ApplyResponse apply(
            @PathVariable String slug,
            @Valid @RequestPart("application") ApplicationDtos.PublicApplyRequest request,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            HttpServletRequest httpRequest) {
        return applicationService.applyPublicly(
                slug, request, resume, RequestContext.from(httpRequest));
    }
}
