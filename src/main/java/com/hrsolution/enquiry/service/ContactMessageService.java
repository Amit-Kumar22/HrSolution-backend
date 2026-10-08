package com.hrsolution.enquiry.service;

import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.enquiry.dto.ContactMessageDtos;
import com.hrsolution.enquiry.entity.ContactMessage;
import com.hrsolution.enquiry.mapper.EnquiryMapper;
import com.hrsolution.enquiry.repository.ContactMessageRepository;
import com.hrsolution.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The general Contact Us inbox.
 *
 * <p>Same spam approach as {@link EnquiryService} - flag, never reject - but a
 * flagged contact message is cheaper to get wrong, so it is marked with a
 * {@code spam} boolean rather than consuming a pipeline status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;
    private final EnquiryMapper enquiryMapper;
    private final SpamGuard spamGuard;
    private final EmailService emailService;

    @Transactional
    public void submit(ContactMessageDtos.SubmitRequest request, RequestContext context) {
        SpamGuard.Verdict verdict = spamGuard.assess(
                request.website(), request.formRenderedAt(), context,
                request.message(), request.subject());

        ContactMessage message = new ContactMessage();
        message.setName(request.name().trim());
        message.setEmail(request.email().trim().toLowerCase());
        message.setPhone(trimToNull(request.phone()));
        message.setSubject(trimToNull(request.subject()));
        message.setMessage(request.message().trim());
        message.setSpam(verdict.suspected());
        message.setIpAddress(context.ipAddress());
        message.setUserAgent(context.userAgent());

        ContactMessage saved = contactMessageRepository.save(message);

        if (verdict.suspected()) {
            log.info("Contact message {} stored as spam: {}", saved.getId(), verdict.reason());
        } else {
            emailService.sendAdminContactNotification(
                    saved.getName(), saved.getEmail(), saved.getPhone(),
                    saved.getSubject(), saved.getMessage());
            log.info("Contact message {} received from {}", saved.getId(), saved.getEmail());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactMessageDtos.Response> list(String search,
                                                          Boolean read,
                                                          boolean includeSpam,
                                                          Pageable pageable) {
        Specification<ContactMessage> specification = Specification.<ContactMessage>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("read", read))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "name", "email", "subject", "message"));

        if (!includeSpam) {
            specification = specification.and(SpecificationUtils.isFalse("spam"));
        }

        Page<ContactMessage> page = contactMessageRepository.findAll(specification, pageable);
        return PageResponse.from(page, enquiryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ContactMessageDtos.Response get(Long id) {
        return enquiryMapper.toResponse(load(id));
    }

    /**
     * Marks a message read.
     *
     * <p>Deliberately an explicit action rather than a side effect of
     * {@link #get}: a staff member scrolling a list should not silently mark
     * everything as handled.
     */
    @Transactional
    public ContactMessageDtos.Response markRead(Long id, boolean read) {
        ContactMessage message = load(id);
        if (read) {
            message.markRead();
        } else {
            message.markUnread();
        }
        return enquiryMapper.toResponse(message);
    }

    @Transactional
    public ContactMessageDtos.Response markReplied(Long id) {
        ContactMessage message = load(id);
        message.setReplied(true);
        message.markRead();
        return enquiryMapper.toResponse(message);
    }

    @Transactional
    public ContactMessageDtos.Response markSpam(Long id, boolean spam) {
        ContactMessage message = load(id);
        message.setSpam(spam);
        return enquiryMapper.toResponse(message);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser actor, RequestContext context) {
        load(id).markDeleted(actor.email());
    }

    /** Unread count for the admin notification badge. */
    @Transactional(readOnly = true)
    public long unreadCount() {
        return contactMessageRepository.countByReadFalseAndSpamFalseAndDeletedFalse();
    }

    private ContactMessage load(Long id) {
        return contactMessageRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Contact message", id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
