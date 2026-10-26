package com.hrsolution.report.controller;

import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(ApiPaths.V1 + "/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Basic dashboards/reports")
public class ReportController {

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_VIEW + "')")
    public Map<String, Object> attendance(@RequestParam(required = false) Integer month,
                                          @RequestParam(required = false) Integer year) {
        Map<String, Object> res = new HashMap<>();
        res.put("month", month);
        res.put("year", year);
        res.put("summary", "Attendance report placeholder");
        return res;
    }

    @GetMapping("/payroll")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_VIEW + "')")
    public Map<String, Object> payroll(@RequestParam(required = false) Integer month,
                                       @RequestParam(required = false) Integer year) {
        Map<String, Object> res = new HashMap<>();
        res.put("month", month);
        res.put("year", year);
        res.put("summary", "Payroll report placeholder");
        return res;
    }

    @GetMapping("/billing/ageing")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_VIEW + "')")
    public Map<String, Object> billingAgeing() {
        Map<String, Object> res = new HashMap<>();
        res.put("summary", "Billing ageing placeholder");
        return res;
    }

    @GetMapping("/compliance")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_VIEW + "')")
    public Map<String, Object> compliance() {
        Map<String, Object> res = new HashMap<>();
        res.put("summary", "Compliance report placeholder");
        return res;
    }
}
