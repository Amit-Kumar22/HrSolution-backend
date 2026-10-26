package com.hrsolution.payroll.mapper;

import com.hrsolution.payroll.dto.PayrollDtos;
import com.hrsolution.payroll.entity.PayrollItem;
import com.hrsolution.payroll.entity.PayrollItemComponent;
import com.hrsolution.payroll.entity.PayrollRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PayrollMapper {

    @Mapping(target = "clientId", expression = "java(run.getClient() == null ? null : run.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(run.getClient() == null ? null : (run.getClient().getTradeName() != null ? run.getClient().getTradeName() : run.getClient().getLegalName()))")
    @Mapping(target = "allowedNextStatuses", expression = "java(run.getStatus().allowedNext())")
    @Mapping(target = "locked", expression = "java(run.getStatus().isLocked())")
    @Mapping(target = "processedBy", expression = "java(run.getProcessedBy() == null ? null : run.getProcessedBy().fullName())")
    @Mapping(target = "approvedBy", expression = "java(run.getApprovedBy() == null ? null : run.getApprovedBy().fullName())")
    @Mapping(target = "items", ignore = true)
    PayrollDtos.Response toResponse(PayrollRun run);

    @Mapping(target = "clientId", expression = "java(run.getClient() == null ? null : run.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(run.getClient() == null ? null : (run.getClient().getTradeName() != null ? run.getClient().getTradeName() : run.getClient().getLegalName()))")
    @Mapping(target = "itemCount", ignore = true)
    PayrollDtos.SummaryResponse toSummaryResponse(PayrollRun run);

    @Mapping(target = "workerId", expression = "java(item.getWorker().getId())")
    @Mapping(target = "workerName", expression = "java(item.getWorker().fullName())")
    @Mapping(target = "employeeCode", expression = "java(item.getWorker().employeeCode())")
    @Mapping(target = "deploymentId", expression = "java(item.getDeployment().getId())")
    @Mapping(target = "attendanceSheetId", expression = "java(item.getAttendanceSheet() == null ? null : item.getAttendanceSheet().getId())")
    PayrollDtos.ItemResponse toItemResponse(PayrollItem item);

    PayrollDtos.ComponentResponse toComponentResponse(PayrollItemComponent component);
}
