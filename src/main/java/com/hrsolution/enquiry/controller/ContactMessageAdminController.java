package com.hrsolution.enquiry.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.enquiry.dto.ContactMessageDtos;
import com.hrsolution.enquiry.service.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Contact Us inbox, for staff. */
@RestController
@RequestMapping(ApiPaths.V1 + "/contact-messages")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Enquiries", description = "Manpower enquiries from the website")
public class ContactMessageAdminController {

    private final ContactMessageService contactMessageService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "List contact messages",
            description = "Newest first. Suspected spam is hidden unless includeSpam=true.")
    public PageResponse<ContactMessageDtos.Response> list(
            @Parameter(description = "Free text across name, email, subject and message")
            @RequestParam(required = false) String search,
            @Parameter(description = "true for read only, false for unread only")
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "false") boolean includeSpam,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return contactMessageService.list(search, read, includeSpam, pageable);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Count unread messages",
            description = "For the notification badge. Excludes spam and deleted messages.")
    public java.util.Map<String, Long> unreadCount() {
        return java.util.Map.of("unread", contactMessageService.unreadCount());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Get one message",
            description = "Does NOT mark it read - that is an explicit action, so scrolling a "
                    + "list cannot silently mark everything as handled.")
    public ContactMessageDtos.Response get(@PathVariable Long id) {
        return contactMessageService.get(id);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Mark a message read or unread")
    public ContactMessageDtos.Response markRead(
            @PathVariable Long id, @RequestParam(defaultValue = "true") boolean read) {
        return contactMessageService.markRead(id, read);
    }

    @PatchMapping("/{id}/replied")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Mark a message as replied to",
            description = "Also marks it read, since you cannot reply to something unread.")
    public ContactMessageDtos.Response markReplied(@PathVariable Long id) {
        return contactMessageService.markReplied(id);
    }

    @PatchMapping("/{id}/spam")
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Flag or unflag a message as spam",
            description = "Use spam=false to recover one the automatic checks got wrong.")
    public ContactMessageDtos.Response markSpam(
            @PathVariable Long id, @RequestParam(defaultValue = "true") boolean spam) {
        return contactMessageService.markSpam(id, spam);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.ENQUIRY_MANAGE + "')")
    @Operation(summary = "Delete a message (soft)")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        contactMessageService.delete(id, caller, RequestContext.from(httpRequest));
    }
}
