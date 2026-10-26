package com.hrsolution.compliance.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.client.service.ClientAccessGuard;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.compliance.dto.ComplianceDtos;
import com.hrsolution.compliance.entity.Licence;
import com.hrsolution.compliance.entity.LicenceStatus;
import com.hrsolution.compliance.entity.StatutoryChallan;
import com.hrsolution.compliance.mapper.ComplianceMapper;
import com.hrsolution.compliance.repository.LicenceRepository;
import com.hrsolution.compliance.repository.StatutoryChallanRepository;
import com.hrsolution.notification.entity.Notification;
import com.hrsolution.notification.entity.NotificationType;
import com.hrsolution.notification.repository.NotificationRepository;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final LicenceRepository licenceRepository;
    private final StatutoryChallanRepository challanRepository;
    private final ClientRepository clientRepository;
    private final ClientAccessGuard accessGuard;
    private final ComplianceMapper mapper;
    private final AuditService auditService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<ComplianceDtos.LicenceResponse> listLicences(Long requestedClientId,
                                                                     AuthenticatedUser caller,
                                                                     Pageable pageable) {
        if (requestedClientId != null) {
            accessGuard.requireAccessTo(requestedClientId, caller);
        }
        Page<Licence> page = licenceRepository.findAll(pageable);
        return PageResponse.from(page, mapper::toLicenceResponse);
    }

    @Transactional
    public ComplianceDtos.LicenceResponse createLicence(ComplianceDtos.LicenceRequest request,
                                                       AuthenticatedUser caller,
                                                       RequestContext context) {
        Licence licence = new Licence();
        applyLicence(licence, request);
        Licence saved = licenceRepository.saveAndFlush(licence);
        auditService.recordEntityChange(AuditAction.CREATE, "Licence", saved.getId(),
                caller.id(), caller.email(), "Created licence " + saved.getLicenceNumber(),
                null, saved.getLicenceNumber(), context);
        return mapper.toLicenceResponse(saved);
    }

    @Transactional
    public ComplianceDtos.LicenceResponse updateLicence(Long id, ComplianceDtos.LicenceRequest request,
                                                       AuthenticatedUser caller,
                                                       RequestContext context) {
        Licence licence = licenceRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("Licence", id));
        applyLicence(licence, request);
        Licence saved = licenceRepository.saveAndFlush(licence);
        auditService.recordEntityChange(AuditAction.UPDATE, "Licence", saved.getId(),
                caller.id(), caller.email(), "Updated licence " + saved.getLicenceNumber(),
                null, null, context);
        return mapper.toLicenceResponse(saved);
    }

    private void applyLicence(Licence licence, ComplianceDtos.LicenceRequest request) {
        licence.setLicenceType(request.licenceType());
        licence.setLicenceNumber(request.licenceNumber());
        if (request.clientId() != null) {
            Client client = clientRepository.findById(request.clientId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Client", request.clientId()));
            licence.setClient(client);
        } else {
            licence.setClient(null);
        }
        licence.setIssueDate(request.issueDate());
        licence.setExpiryDate(request.expiryDate());
        licence.setDocumentPath(request.documentPath());
        licence.setStatus(request.status() == null ? LicenceStatus.ACTIVE : request.status());
        licence.setIssuingAuthority(request.issuingAuthority());
        licence.setNotes(request.notes());
    }

    @Transactional(readOnly = true)
    public List<ComplianceDtos.ReminderResponse> reminders(int withinDays) {
        LocalDate now = LocalDate.now();
        LocalDate to = now.plusDays(withinDays);
        List<Licence> licences = licenceRepository.findByExpiryDateBetweenAndDeletedFalse(now, to);
        List<ComplianceDtos.ReminderResponse> result = new ArrayList<>();
        for (Licence l : licences) {
            long days = l.daysUntilExpiry();
            String level = days <= 7 ? "CRITICAL" : days <= 30 ? "HIGH" : "MEDIUM";
            String clientName = l.getClient() == null ? null
                    : (l.getClient().getTradeName() != null ? l.getClient().getTradeName() : l.getClient().getLegalName());
            result.add(new ComplianceDtos.ReminderResponse(
                    l.getId(), l.getLicenceType().name(), l.getLicenceNumber(),
                    clientName, l.getExpiryDate(), days, level));
        }
        return result;
    }

    @Transactional
    public void generateReminders() {
        List<ComplianceDtos.ReminderResponse> list = reminders(60);
        if (list.isEmpty()) {
            return;
        }
        // Notify all users with COMPLIANCE_MANAGE (broadly admins/staff) - simplified: notify users who are not clients
        List<User> users = userRepository.findAll();
        StringBuilder sb = new StringBuilder("Expiring licences (60 days): ");
        for (ComplianceDtos.ReminderResponse r : list) {
            sb.append(r.licenceType()).append(" ").append(r.licenceNumber())
                    .append(" in ").append(r.daysUntilExpiry()).append("d; ");
        }
        String msg = sb.toString();
        for (User u : users) {
            if (u.isDeleted()) continue;
            Notification n = new Notification();
            n.setUser(u);
            n.setTitle("Licence expiry reminders");
            n.setMessage(msg.length() > 950 ? msg.substring(0, 950) + "..." : msg);
            n.setType(NotificationType.WARNING);
            n.setLink("/compliance/licences");
            notificationRepository.save(n);
        }
        log.info("Generated {} licence reminders across {} users", list.size(), users.size());
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplianceDtos.ChallanResponse> listChallans(Pageable pageable) {
        Page<StatutoryChallan> page = challanRepository.findAll(pageable);
        return PageResponse.from(page, mapper::toChallanResponse);
    }

    @Transactional
    public ComplianceDtos.ChallanResponse createChallan(ComplianceDtos.ChallanRequest request,
                                                       AuthenticatedUser caller,
                                                       RequestContext context) {
        StatutoryChallan challan = new StatutoryChallan();
        challan.setChallanType(request.challanType());
        challan.setMonth(request.month());
        challan.setYear(request.year());
        challan.setAmount(request.amount());
        challan.setPaidOn(request.paidOn());
        challan.setChallanNumber(request.challanNumber());
        challan.setDocumentPath(request.documentPath());
        challan.setReturnFiled(Boolean.TRUE.equals(request.returnFiled()));
        challan.setNotes(request.notes());
        StatutoryChallan saved = challanRepository.saveAndFlush(challan);
        auditService.recordEntityChange(AuditAction.CREATE, "StatutoryChallan", saved.getId(),
                caller.id(), caller.email(), "Created challan " + saved.getChallanType() + " " + saved.getMonth() + "/" + saved.getYear(),
                null, null, context);
        return mapper.toChallanResponse(saved);
    }
}
