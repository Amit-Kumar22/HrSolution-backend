package com.hrsolution.holiday.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.holiday.dto.HolidayDtos;
import com.hrsolution.holiday.entity.Holiday;
import com.hrsolution.holiday.mapper.HolidayMapper;
import com.hrsolution.holiday.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final HolidayMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<HolidayDtos.Response> list(LocalDate from, LocalDate to, String state) {
        LocalDate f = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate t = to == null ? f.plusMonths(1).minusDays(1) : to;
        return holidayRepository.findApplicable(f, t, state).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public HolidayDtos.Response create(HolidayDtos.Request request,
                                       AuthenticatedUser actor,
                                       RequestContext context) {
        Holiday holiday = mapper.toEntity(request);
        Holiday saved = holidayRepository.save(holiday);
        auditService.recordEntityChange(AuditAction.CREATE, "Holiday", saved.getId(),
                actor.id(), actor.email(), "Created holiday " + saved.getName(),
                null, null, context);
        return mapper.toResponse(saved);
    }

    @Transactional
    public HolidayDtos.Response update(Long id, HolidayDtos.Request request,
                                       AuthenticatedUser actor,
                                       RequestContext context) {
        Holiday holiday = holidayRepository.findById(id)
                .filter(h -> !h.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("Holiday", id));
        mapper.apply(request, holiday);
        auditService.recordEntityChange(AuditAction.UPDATE, "Holiday", id,
                actor.id(), actor.email(), "Updated holiday " + holiday.getName(),
                null, null, context);
        return mapper.toResponse(holiday);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser actor, RequestContext context) {
        Holiday holiday = holidayRepository.findById(id)
                .filter(h -> !h.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("Holiday", id));
        holiday.markDeleted(actor.email());
        auditService.recordEntityChange(AuditAction.DELETE, "Holiday", id,
                actor.id(), actor.email(), "Deleted holiday " + holiday.getName(),
                null, null, context);
    }
}
