package com.hrsolution.holiday.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.holiday.dto.HolidayDtos;
import com.hrsolution.holiday.service.HolidayService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(ApiPaths.V1 + "/holidays")
@RequiredArgsConstructor
@Tag(name = "Holidays", description = "Holiday calendar")
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public List<HolidayDtos.Response> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state) {
        return holidayService.list(from, to, state);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    @SecurityRequirement(name = "bearerAuth")
    public HolidayDtos.Response create(@Valid @RequestBody HolidayDtos.Request request,
                                        @AuthenticationPrincipal AuthenticatedUser actor,
                                        HttpServletRequest httpRequest) {
        return holidayService.create(request, actor, RequestContext.from(httpRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    @SecurityRequirement(name = "bearerAuth")
    public HolidayDtos.Response update(@PathVariable Long id,
                                       @Valid @RequestBody HolidayDtos.Request request,
                                       @AuthenticationPrincipal AuthenticatedUser actor,
                                       HttpServletRequest httpRequest) {
        return holidayService.update(id, request, actor, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal AuthenticatedUser actor,
                       HttpServletRequest httpRequest) {
        holidayService.delete(id, actor, RequestContext.from(httpRequest));
    }
}
