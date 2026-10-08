package com.hrsolution.enquiry.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.enquiry.EnquiryReference;
import com.hrsolution.enquiry.dto.EnquiryDtos;
import com.hrsolution.enquiry.entity.Enquiry;
import com.hrsolution.enquiry.entity.EnquiryStatus;
import com.hrsolution.enquiry.mapper.EnquiryMapper;
import com.hrsolution.enquiry.repository.EnquiryRepository;
import com.hrsolution.notification.service.EmailService;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Manpower enquiries - the top of the sales funnel.
 *
 * <p>Public submission is intentionally forgiving. Beyond the three mandatory
 * fields, anything the visitor leaves out is simply absent, and a suspected-spam
 * submission is <strong>stored and flagged rather than rejected</strong>. The
 * reasoning is in {@link SpamGuard}: a false positive here throws away a real
 * sales lead, which is a worse outcome than a flagged row a human can unflag.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final EnquiryMapper enquiryMapper;
    private final SpamGuard spamGuard;
    private final EmailService emailService;
    private final AuditService auditService;

    // ==================================================================
    // Public submission
    // ==================================================================

    @Transactional
    public EnquiryDtos.SubmitResponse submit(EnquiryDtos.SubmitRequest request,
                                             RequestContext context) {
        SpamGuard.Verdict verdict = spamGuard.assess(
                request.website(), request.formRenderedAt(), context,
                request.message(), request.companyName(), request.otherCategory());

        Enquiry enquiry = new Enquiry();
        enquiry.setCompanyName(request.companyName().trim());
        enquiry.setContactPerson(request.contactPerson().trim());
        enquiry.setPhone(request.phone().trim());
        enquiry.setEmail(trimToNull(request.email()));
        enquiry.setCity(trimToNull(request.city()));
        enquiry.setState(trimToNull(request.state()));
        enquiry.setCategory(resolveCategory(request.categoryId()));
        enquiry.setOtherCategory(trimToNull(request.otherCategory()));
        enquiry.setNumberOfWorkers(request.numberOfWorkers());
        enquiry.setDurationMonths(request.durationMonths());
        enquiry.setRequiredFrom(request.requiredFrom());
        enquiry.setMessage(trimToNull(request.message()));
        enquiry.setIpAddress(context.ipAddress());
        enquiry.setUserAgent(context.userAgent());

        enquiry.setStatus(verdict.suspected() ? EnquiryStatus.SPAM : EnquiryStatus.NEW);
        if (verdict.suspected()) {
            enquiry.appendInternalNote("Flagged automatically: " + verdict.reason(), "system");
        }

        Enquiry saved = enquiryRepository.save(enquiry);
        String reference = EnquiryReference.of(saved.getId());

        if (verdict.suspected()) {
            log.info("Enquiry {} stored as SPAM: {}", reference, verdict.reason());
        } else {
            // Only genuine leads notify the sales team, or the honeypot would
            // make the notification worthless.
            emailService.sendAdminEnquiryNotification(
                    reference, saved.getCompanyName(), saved.getContactPerson(),
                    saved.getPhone(), saved.getEmail(), saved.categoryLabel(),
                    saved.getNumberOfWorkers(), saved.getCity(), saved.getMessage());
            log.info("Enquiry {} received from '{}' for {}",
                    reference, saved.getCompanyName(), saved.categoryLabel());
        }

        // The acknowledgement is identical either way. Telling a bot it was
        // detected only helps it iterate.
        return new EnquiryDtos.SubmitResponse(reference,
                "Thank you. Your enquiry has been received and our team will contact you shortly. "
                        + "Please quote reference " + reference + " when following up.");
    }

    /**
     * Resolves the chosen category, tolerating an unknown or inactive id.
     *
     * <p>A stale dropdown on a cached page must not cost a lead, so an id that
     * no longer resolves is dropped rather than rejected - the free-text field
     * and the message still carry the requirement.
     */
    private ManpowerCategory resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .filter(ManpowerCategory::isActive)
                .orElseGet(() -> {
                    log.info("Enquiry referenced category id {} which is unknown or inactive; "
                            + "continuing without it", categoryId);
                    return null;
                });
    }

    // ==================================================================
    // Staff
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<EnquiryDtos.Response> list(String search,
                                                   EnquiryStatus status,
                                                   Long categoryId,
                                                   Long assignedToUserId,
                                                   LocalDate from,
                                                   LocalDate to,
                                                   boolean includeSpam,
                                                   Pageable pageable) {
        Specification<Enquiry> specification = Specification.<Enquiry>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("category.id", categoryId))
                .and(SpecificationUtils.equal("assignedTo.id", assignedToUserId))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "companyName", "contactPerson", "phone", "email", "city"))
                .and(SpecificationUtils.greaterOrEqual("createdAt",
                        from == null ? null : from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)))
                .and(SpecificationUtils.lessOrEqual("createdAt",
                        to == null ? null : to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));

        // Spam is hidden unless asked for, so the default view is the real
        // pipeline. An explicit filter on SPAM still works.
        if (!includeSpam && status != EnquiryStatus.SPAM) {
            specification = specification.and(SpecificationUtils.notEqual("status", EnquiryStatus.SPAM));
        }

        Page<Enquiry> page = enquiryRepository.findAll(specification, pageable);
        return PageResponse.from(page, enquiryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EnquiryDtos.Response get(Long id) {
        return enquiryMapper.toResponse(load(id));
    }

    @Transactional
    public EnquiryDtos.Response updateStatus(Long id,
                                             EnquiryDtos.UpdateStatusRequest request,
                                             AuthenticatedUser actor,
                                             RequestContext context) {
        Enquiry enquiry = load(id);
        EnquiryStatus previous = enquiry.getStatus();

        enquiry.setStatus(request.status());
        enquiry.appendInternalNote(
                "Status %s -> %s%s".formatted(previous, request.status(),
                        request.note() == null ? "" : ". " + request.note()),
                actor.email());

        auditService.recordEntityChange(AuditAction.UPDATE, "Enquiry", enquiry.getId(),
                actor.id(), actor.email(),
                "Enquiry %s status changed".formatted(EnquiryReference.of(enquiry.getId())),
                previous.name(), request.status().name(), context);

        return enquiryMapper.toResponse(enquiry);
    }

    @Transactional
    public EnquiryDtos.Response assign(Long id,
                                       EnquiryDtos.AssignRequest request,
                                       AuthenticatedUser actor,
                                       RequestContext context) {
        Enquiry enquiry = load(id);
        String previous = enquiry.getAssignedTo() == null
                ? "nobody" : enquiry.getAssignedTo().getEmail();

        User assignee = null;
        if (request.assignedToUserId() != null) {
            assignee = userRepository.findActiveByIdWithRoles(request.assignedToUserId())
                    .orElseThrow(() -> FieldValidationException.of("assignedToUserId",
                            "is not a known active user"));
        }
        enquiry.setAssignedTo(assignee);
        enquiry.appendInternalNote(
                "Reassigned from %s to %s%s".formatted(previous,
                        assignee == null ? "nobody" : assignee.getEmail(),
                        request.note() == null ? "" : ". " + request.note()),
                actor.email());

        auditService.recordEntityChange(AuditAction.UPDATE, "Enquiry", enquiry.getId(),
                actor.id(), actor.email(), "Enquiry reassigned",
                previous, assignee == null ? "nobody" : assignee.getEmail(), context);

        return enquiryMapper.toResponse(enquiry);
    }

    /** Soft delete: an enquiry records how a client relationship began. */
    @Transactional
    public void delete(Long id, AuthenticatedUser actor, RequestContext context) {
        Enquiry enquiry = load(id);
        enquiry.markDeleted(actor.email());

        auditService.recordEntityChange(AuditAction.DELETE, "Enquiry", enquiry.getId(),
                actor.id(), actor.email(),
                "Enquiry %s deleted".formatted(EnquiryReference.of(enquiry.getId())),
                null, null, context);
    }

    @Transactional(readOnly = true)
    public long countByStatus(EnquiryStatus status) {
        return enquiryRepository.countByStatusAndDeletedFalse(status);
    }

    /** Database-backed abuse check, behind the in-memory rate limiter. */
    @Transactional(readOnly = true)
    public long countRecentFromIp(String ipAddress, Instant since) {
        return ipAddress == null ? 0 : enquiryRepository.countRecentFromIp(ipAddress, since);
    }

    private Enquiry load(Long id) {
        return enquiryRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Enquiry", id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
