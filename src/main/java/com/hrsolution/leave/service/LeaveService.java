package com.hrsolution.leave.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.leave.dto.LeaveDtos;
import com.hrsolution.leave.entity.LeaveRequest;
import com.hrsolution.leave.entity.LeaveStatus;
import com.hrsolution.leave.mapper.LeaveMapper;
import com.hrsolution.leave.repository.LeaveRequestRepository;
import com.hrsolution.user.repository.UserRepository;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final WorkerRepository workerRepository;
    private final LeaveMapper mapper;
    private final AuditService auditService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<LeaveDtos.Response> list(Long workerId, LeaveStatus status,
                                                AuthenticatedUser caller,
                                                Pageable pageable) {
        Specification<LeaveRequest> spec = Specification.<LeaveRequest>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("worker.id", workerId))
                .and(SpecificationUtils.equal("status", status));
        Page<LeaveRequest> page = leaveRepository.findAll(spec, pageable);
        return PageResponse.from(page, mapper::toResponse);
    }

    @Transactional
    public LeaveDtos.Response apply(LeaveDtos.SelfRequest request,
                                    AuthenticatedUser caller,
                                    RequestContext context) {
        Worker worker = workerRepository.findActiveByUserId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("Worker", caller.id()));
        if (request.fromDate().isAfter(request.toDate())) {
            throw new BusinessRuleException("The end date cannot be before the start date.");
        }
        List<LeaveRequest> overlapping = leaveRepository.findOverlapping(worker.getId(),
                request.fromDate(), request.toDate());
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException("You already have a pending or approved leave overlapping these dates.");
        }
        LeaveRequest leave = new LeaveRequest();
        leave.setWorker(worker);
        leave.setFromDate(request.fromDate());
        leave.setToDate(request.toDate());
        leave.setLeaveType(request.leaveType());
        leave.setReason(request.reason());
        leave.setStatus(LeaveStatus.PENDING);
        LeaveRequest saved = leaveRepository.save(leave);
        auditService.recordEntityChange(AuditAction.CREATE, "LeaveRequest", saved.getId(),
                caller.id(), caller.email(), "Applied for %s leave".formatted(request.leaveType()),
                null, null, context);
        return mapper.toResponse(saved);
    }

    @Transactional
    public LeaveDtos.Response approve(Long leaveId, LeaveDtos.ActionRequest request,
                                      AuthenticatedUser caller,
                                      RequestContext context) {
        LeaveRequest leave = load(leaveId);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessRuleException("Only pending leave requests can be approved.");
        }
        leave.setStatus(LeaveStatus.APPROVED);
        leave.setApprovedBy(userRepository.findById(caller.id()).orElse(null));
        leave.setApprovedAt(Instant.now());
        leave.setRemarks(request == null ? null : request.remarks());
        auditService.recordEntityChange(AuditAction.UPDATE, "LeaveRequest", leaveId,
                caller.id(), caller.email(), "Approved leave request", null, null, context);
        return mapper.toResponse(leave);
    }

    @Transactional
    public LeaveDtos.Response reject(Long leaveId, LeaveDtos.ActionRequest request,
                                     AuthenticatedUser caller,
                                     RequestContext context) {
        LeaveRequest leave = load(leaveId);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessRuleException("Only pending leave requests can be rejected.");
        }
        if (request == null || request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new BusinessRuleException("A rejection reason is required.");
        }
        leave.setStatus(LeaveStatus.REJECTED);
        leave.setApprovedBy(userRepository.findById(caller.id()).orElse(null));
        leave.setApprovedAt(Instant.now());
        leave.setRejectionReason(request.rejectionReason());
        leave.setRemarks(request.remarks());
        auditService.recordEntityChange(AuditAction.UPDATE, "LeaveRequest", leaveId,
                caller.id(), caller.email(), "Rejected leave request", null, null, context);
        return mapper.toResponse(leave);
    }

    @Transactional
    public LeaveDtos.Response cancel(Long leaveId, AuthenticatedUser caller, RequestContext context) {
        LeaveRequest leave = load(leaveId);
        if (!leave.getStatus().canCancel()) {
            throw new BusinessRuleException("Only pending leave requests can be cancelled.");
        }
        leave.setStatus(LeaveStatus.CANCELLED);
        auditService.recordEntityChange(AuditAction.UPDATE, "LeaveRequest", leaveId,
                caller.id(), caller.email(), "Cancelled leave request", null, null, context);
        return mapper.toResponse(leave);
    }

    private LeaveRequest load(Long leaveId) {
        return leaveRepository.findById(leaveId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("LeaveRequest", leaveId));
    }
}
