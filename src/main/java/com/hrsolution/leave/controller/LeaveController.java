package com.hrsolution.leave.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.leave.dto.LeaveDtos;
import com.hrsolution.leave.entity.LeaveStatus;
import com.hrsolution.leave.service.LeaveService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/leave")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Leave", description = "Leave requests and approvals")
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "')")
    public PageResponse<LeaveDtos.Response> list(@RequestParam(required = false) Long workerId,
                                                 @RequestParam(required = false) LeaveStatus status,
                                                 @AuthenticationPrincipal AuthenticatedUser caller,
                                                 @ParameterObject
                                                 @PageableDefault(size = 20) Pageable pageable) {
        return leaveService.list(workerId, status, caller, pageable);
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.SELF_LEAVE_MANAGE + "')")
    public LeaveDtos.Response apply(@Valid @RequestBody LeaveDtos.SelfRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser caller,
                                    HttpServletRequest httpRequest) {
        return leaveService.apply(request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{leaveId}/approve")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "')")
    public LeaveDtos.Response approve(@PathVariable Long leaveId,
                                      @RequestBody(required = false) LeaveDtos.ActionRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        return leaveService.approve(leaveId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{leaveId}/reject")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "')")
    public LeaveDtos.Response reject(@PathVariable Long leaveId,
                                     @Valid @RequestBody LeaveDtos.ActionRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser caller,
                                     HttpServletRequest httpRequest) {
        return leaveService.reject(leaveId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{leaveId}/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.SELF_LEAVE_MANAGE + "')")
    public LeaveDtos.Response cancel(@PathVariable Long leaveId,
                                     @AuthenticationPrincipal AuthenticatedUser caller,
                                     HttpServletRequest httpRequest) {
        return leaveService.cancel(leaveId, caller, RequestContext.from(httpRequest));
    }
}
