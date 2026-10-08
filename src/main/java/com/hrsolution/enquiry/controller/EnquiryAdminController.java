package com.hrsolution.enquiry.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.enquiry.dto.EnquiryDtos;
import com.hrsolution.enquiry.entity.EnquiryStatus;
import com.hrsolution.enquiry.service.EnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The enquiry pipeline, for staff.
 *
 * <p>Spam is excluded from the default list so the pipeline shows real leads.
 * It is still reachable - pass {@code includeSpam=true} or filter on
 * {@code status=SPAM} - because the spam guard errs towards flagging, and a
 * wrongly-flagged lead has to be recoverable.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/enquiries")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Enquiries", description = "Manpower enquiries from the website")
public class EnquiryAdminController {

    private final EnquiryService enquiryService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "List enquiries",
            description = "Newest first. Suspected spam is hidden unless includeSpam=true or you "
                    + "filter explicitly on status=SPAM.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of enquiries"),
            @ApiResponse(responseCode = "403", description = "Missing ENQUIRY_MANAGE (ACCESS_DENIED)")
    })
    public PageResponse<EnquiryDtos.Response> list(
            @Parameter(description = "Free text across company, contact, phone, email and city")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EnquiryStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long assignedToUserId,
            @Parameter(description = "Submitted on or after this date (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Submitted on or before this date (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Include submissions flagged as spam")
            @RequestParam(defaultValue = "false") boolean includeSpam,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return enquiryService.list(search, status, categoryId, assignedToUserId,
                from, to, includeSpam, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Get one enquiry, including internal notes")
    public EnquiryDtos.Response get(@PathVariable Long id) {
        return enquiryService.get(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Move an enquiry along the pipeline",
            description = "The change is appended to the internal notes with your name and a "
                    + "timestamp, and recorded in the audit log. Use status=SPAM to flag junk, or "
                    + "move a wrongly-flagged one back to NEW.")
    public EnquiryDtos.Response updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryDtos.UpdateStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return enquiryService.updateStatus(id, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Assign an enquiry to a staff member",
            description = "Pass assignedToUserId=null to unassign.")
    public EnquiryDtos.Response assign(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryDtos.AssignRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return enquiryService.assign(id, request, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Delete an enquiry",
            description = "Soft delete - an enquiry records how a client relationship began, so "
                    + "the row is retained and the deletion is audited.")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        enquiryService.delete(id, caller, RequestContext.from(httpRequest));
    }
}
