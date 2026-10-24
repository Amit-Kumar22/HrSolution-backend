package com.hrsolution.attendance.controller;

import com.hrsolution.attendance.dto.AttendanceDtos;
import com.hrsolution.attendance.entity.AttendanceSheetStatus;
import com.hrsolution.attendance.service.AttendanceService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/attendance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Attendance", description = "Attendance sheets, marking and approval")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_READ + "')")
    public PageResponse<AttendanceDtos.SummaryResponse> list(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false, defaultValue = "0") int month,
            @RequestParam(required = false, defaultValue = "0") int year,
            @RequestParam(required = false) AttendanceSheetStatus status,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject
            @PageableDefault(size = 20, sort = "year", direction = Sort.Direction.DESC) Pageable pageable) {
        return attendanceService.list(clientId, siteId, month, year, status, caller, pageable);
    }

    @GetMapping("/{sheetId}")
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_READ + "')")
    public AttendanceDtos.Response get(@PathVariable Long sheetId,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return attendanceService.get(sheetId, caller);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_MARK + "')")
    @Operation(summary = "Generate a monthly attendance sheet from active deployments")
    public AttendanceDtos.Response generate(@RequestParam Long siteId,
                                             @RequestParam int month,
                                             @RequestParam int year,
                                             @AuthenticationPrincipal AuthenticatedUser caller,
                                             HttpServletRequest request) {
        return attendanceService.generate(siteId, month, year, caller, RequestContext.from(request));
    }

    @PutMapping("/{sheetId}/entries")
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_MARK + "')")
    public AttendanceDtos.Response saveEntries(@PathVariable Long sheetId,
                                                @Valid @RequestBody AttendanceDtos.BulkEntryRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser caller,
                                                HttpServletRequest httpRequest) {
        return attendanceService.saveEntries(sheetId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{sheetId}/submit")
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_MARK + "')")
    public AttendanceDtos.Response submit(@PathVariable Long sheetId,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest request) {
        return attendanceService.submit(sheetId, caller, RequestContext.from(request));
    }

    @PatchMapping("/{sheetId}/approve")
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_APPROVE + "')")
    public AttendanceDtos.Response approve(@PathVariable Long sheetId,
                                           @AuthenticationPrincipal AuthenticatedUser caller,
                                           HttpServletRequest request) {
        return attendanceService.approve(sheetId, caller, RequestContext.from(request));
    }

    @PatchMapping("/{sheetId}/lock")
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_APPROVE + "')")
    public AttendanceDtos.Response lock(@PathVariable Long sheetId,
                                        @AuthenticationPrincipal AuthenticatedUser caller,
                                        HttpServletRequest request) {
        return attendanceService.lock(sheetId, caller, RequestContext.from(request));
    }

    @PatchMapping("/{sheetId}/unlock")
    @PreAuthorize("hasAuthority('" + Permissions.ATTENDANCE_APPROVE + "')")
    public AttendanceDtos.Response unlock(@PathVariable Long sheetId,
                                          @Valid @RequestBody AttendanceDtos.UnlockRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest httpRequest) {
        return attendanceService.unlock(sheetId, request, caller, RequestContext.from(httpRequest));
    }
}
