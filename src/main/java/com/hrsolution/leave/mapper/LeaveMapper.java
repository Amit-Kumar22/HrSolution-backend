package com.hrsolution.leave.mapper;

import com.hrsolution.leave.dto.LeaveDtos;
import com.hrsolution.leave.entity.LeaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface LeaveMapper {

    @Mapping(target = "workerId", expression = "java(leave.getWorker().getId())")
    @Mapping(target = "workerName", expression = "java(leave.getWorker().fullName())")
    @Mapping(target = "employeeCode", expression = "java(leave.getWorker().employeeCode())")
    @Mapping(target = "allowedNextStatuses", expression = "java(leave.getStatus().allowedNext())")
    @Mapping(target = "terminal", expression = "java(leave.getStatus().isTerminal())")
    @Mapping(target = "approvedByName", expression = "java(leave.getApprovedBy() == null ? null : leave.getApprovedBy().fullName())")
    LeaveDtos.Response toResponse(LeaveRequest leave);
}
