package com.hrsolution.attendance.mapper;

import com.hrsolution.attendance.dto.AttendanceDtos;
import com.hrsolution.attendance.entity.AttendanceEntry;
import com.hrsolution.attendance.entity.AttendanceSheet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AttendanceMapper {

    @Mapping(target = "workerId", expression = "java(entry.getWorker().getId())")
    @Mapping(target = "workerName", expression = "java(entry.getWorker().fullName())")
    @Mapping(target = "employeeCode", expression = "java(entry.getWorker().employeeCode())")
    @Mapping(target = "deploymentId", expression = "java(entry.getDeployment().getId())")
    AttendanceDtos.EntryResponse toEntryResponse(AttendanceEntry entry);

    @Mapping(target = "clientId", expression = "java(sheet.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(sheet.getClient().getCompanyName())")
    @Mapping(target = "siteId", expression = "java(sheet.getSite().getId())")
    @Mapping(target = "siteCode", expression = "java(sheet.getSite().getSiteCode())")
    @Mapping(target = "siteName", expression = "java(sheet.getSite().getSiteName())")
    @Mapping(target = "allowedNextStatuses", expression = "java(sheet.getStatus().allowedNext())")
    @Mapping(target = "locked", expression = "java(sheet.getStatus().isLocked())")
    @Mapping(target = "submittedBy", expression = "java(sheet.getSubmittedBy() == null ? null : sheet.getSubmittedBy().fullName())")
    @Mapping(target = "approvedBy", expression = "java(sheet.getApprovedBy() == null ? null : sheet.getApprovedBy().fullName())")
    @Mapping(target = "lockedBy", expression = "java(sheet.getLockedBy() == null ? null : sheet.getLockedBy().fullName())")
    @Mapping(target = "unlockedBy", expression = "java(sheet.getUnlockedBy() == null ? null : sheet.getUnlockedBy().fullName())")
    @Mapping(target = "entries", ignore = true)
    AttendanceDtos.Response toResponse(AttendanceSheet sheet);

    @Mapping(target = "clientId", expression = "java(sheet.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(sheet.getClient().getCompanyName())")
    @Mapping(target = "siteId", expression = "java(sheet.getSite().getId())")
    @Mapping(target = "siteCode", expression = "java(sheet.getSite().getSiteCode())")
    @Mapping(target = "siteName", expression = "java(sheet.getSite().getSiteName())")
    @Mapping(target = "entryCount", ignore = true)
    AttendanceDtos.SummaryResponse toSummaryResponse(AttendanceSheet sheet);
}
