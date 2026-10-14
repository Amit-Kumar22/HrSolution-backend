package com.hrsolution.recruitment.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.storage.FileTypeDetector;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.document.service.DocumentService;
import com.hrsolution.recruitment.dto.CandidateDtos;
import com.hrsolution.recruitment.entity.Candidate;
import com.hrsolution.recruitment.entity.CandidateSource;
import com.hrsolution.recruitment.mapper.RecruitmentMapper;
import com.hrsolution.recruitment.repository.ApplicationRepository;
import com.hrsolution.recruitment.repository.CandidateRepository;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Candidates - the people in the hiring pipeline.
 *
 * <h2>The identity rule</h2>
 *
 * <p>Email is unique in this table, and {@link #resolveForApplication} is where
 * that matters. An anonymous application can arrive quoting an address that
 * already belongs to a candidate - possibly one with a login. The row is
 * <strong>reused</strong>, because four candidate rows for one person defeats
 * the purpose of having a pipeline, but <strong>nothing on it is
 * overwritten</strong> once it belongs to a registered user.
 *
 * <p>Without that second half, anyone who knew an address could rewrite that
 * person's name, phone number and CV by submitting a public form. With it, the
 * worst an attacker achieves is adding an application to someone else's
 * profile, which is visible to staff and carries no information back to them.
 *
 * <h2>Consent</h2>
 *
 * <p>Nothing here creates a candidate without {@code consentGiven}. The column
 * is {@code NOT NULL} with no database default so the omission would fail
 * loudly rather than defaulting to a convenient {@code true}, and the public
 * apply DTO makes it {@code @AssertTrue}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final RecruitmentMapper mapper;
    private final AuditService auditService;

    // ==================================================================
    // Staff
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<CandidateDtos.SummaryResponse> list(String search,
                                                            Long preferredCategoryId,
                                                            CandidateSource source,
                                                            Boolean hasLogin,
                                                            boolean includeSpam,
                                                            Pageable pageable) {
        Specification<Candidate> specification = Specification.<Candidate>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("preferredCategory.id", preferredCategoryId))
                .and(SpecificationUtils.equal("source", source))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "fullName", "email", "phone", "city"));

        // Spam-flagged candidates are hidden unless asked for, exactly as for
        // enquiries. They are stored rather than rejected because a false
        // positive here loses a real applicant.
        if (!includeSpam) {
            specification = specification.and(SpecificationUtils.equal("spamSuspected", false));
        }
        if (hasLogin != null) {
            specification = specification.and(hasLogin
                    ? (root, query, builder) -> builder.isNotNull(root.get("user"))
                    : (root, query, builder) -> builder.isNull(root.get("user")));
        }

        Page<Candidate> page = candidateRepository.findAll(specification, pageable);

        // One aggregate for the whole page rather than a fetch per row.
        Map<Long, Long> counts = applicationCountsFor(
                page.getContent().stream().map(Candidate::getId).toList());

        return PageResponse.from(page, candidate -> {
            CandidateDtos.SummaryResponse base = mapper.toSummaryResponse(candidate);
            return new CandidateDtos.SummaryResponse(
                    base.id(), base.fullName(), base.email(), base.phone(), base.city(),
                    base.experienceLabel(), base.preferredCategoryName(), base.source(),
                    base.hasLogin(), base.hasResume(), base.spamSuspected(),
                    counts.getOrDefault(candidate.getId(), 0L),
                    base.createdAt());
        });
    }

    private Map<Long, Long> applicationCountsFor(List<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (ApplicationRepository.CandidateApplicationCount row
                : applicationRepository.countByCandidateIds(candidateIds)) {
            counts.put(row.getCandidateId(), row.getTotal());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public CandidateDtos.Response get(Long candidateId) {
        return withApplicationCount(load(candidateId));
    }

    @Transactional
    public CandidateDtos.Response create(CandidateDtos.Request request,
                                         AuthenticatedUser actor,
                                         RequestContext context) {
        String email = normaliseEmail(request.email());

        candidateRepository.findByEmailIgnoringSoftDelete(email).ifPresent(existing -> {
            throw new DuplicateResourceException(
                    ("A candidate with this email already exists (id %d, %s). Open that record "
                            + "and add the application to it, so their history stays in one "
                            + "place.").formatted(existing.getId(), existing.getFullName()));
        });

        if (!Boolean.TRUE.equals(request.consentGiven())) {
            throw new BusinessRuleException(
                    "Consent to hold this person's details is required before a candidate record "
                            + "can be created. Confirm they agreed, then set consentGiven.");
        }

        Candidate candidate = new Candidate();
        mapper.applyRequest(request, candidate);
        candidate.setEmail(email);
        candidate.setPreferredCategory(resolveCategory(request.preferredCategoryId()));
        // Staff-entered candidates default to WALK_IN rather than WEBSITE: the
        // field drives cost-per-hire, and attributing a walk-in to the website
        // would overstate the channel that costs money.
        candidate.setSource(request.source() == null ? CandidateSource.WALK_IN : request.source());
        candidate.giveConsent();

        Candidate saved = candidateRepository.save(candidate);

        auditService.recordEntityChange(AuditAction.CREATE, "Candidate", saved.getId(),
                actor.id(), actor.email(),
                "Created candidate %s (%s)".formatted(saved.getFullName(), saved.getEmail()),
                null, saved.getEmail(), context);

        return withApplicationCount(saved);
    }

    @Transactional
    public CandidateDtos.Response update(Long candidateId,
                                         CandidateDtos.Request request,
                                         AuthenticatedUser actor,
                                         RequestContext context) {
        Candidate candidate = load(candidateId);

        // Email is the identity key and the link to the login, so it is not
        // part of the update. Changing it would silently re-point an
        // application history onto a different person.
        String requested = normaliseEmail(request.email());
        if (!candidate.getEmail().equalsIgnoreCase(requested)) {
            throw new BusinessRuleException(
                    ("A candidate's email cannot be changed - it identifies them across every "
                            + "application. This record is %s. Create a new candidate if this is "
                            + "a different person.").formatted(candidate.getEmail()));
        }

        mapper.applyRequest(request, candidate);
        candidate.setPreferredCategory(resolveCategory(request.preferredCategoryId()));
        if (request.source() != null) {
            candidate.setSource(request.source());
        }
        if (Boolean.TRUE.equals(request.consentGiven())) {
            candidate.giveConsent();
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Candidate", candidateId,
                actor.id(), actor.email(),
                "Updated candidate %s".formatted(candidate.getFullName()),
                null, null, context);

        return withApplicationCount(candidate);
    }

    /** Clears or sets the spam flag on a candidate staff have reviewed. */
    @Transactional
    public CandidateDtos.Response setSpam(Long candidateId,
                                          boolean spam,
                                          AuthenticatedUser actor,
                                          RequestContext context) {
        Candidate candidate = load(candidateId);
        if (spam) {
            candidate.flagAsSpam("Flagged by " + actor.email());
        } else {
            candidate.clearSpamFlag();
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Candidate", candidateId,
                actor.id(), actor.email(),
                "%s candidate %s as spam".formatted(spam ? "Flagged" : "Cleared",
                        candidate.getFullName()),
                null, String.valueOf(spam), context);

        return withApplicationCount(candidate);
    }

    @Transactional
    public CandidateDtos.Response uploadResume(Long candidateId,
                                               MultipartFile file,
                                               AuthenticatedUser actor,
                                               RequestContext context) {
        Candidate candidate = load(candidateId);
        StoredDocument document = storeResume(file, candidateId);
        candidate.setResumeDocument(document);

        auditService.recordEntityChange(AuditAction.UPDATE, "Candidate", candidateId,
                actor.id(), actor.email(),
                "Uploaded CV '%s' for %s".formatted(
                        document.getOriginalFileName(), candidate.getFullName()),
                null, document.getStorageKey(), context);

        return withApplicationCount(candidate);
    }

    /**
     * Soft-deletes a candidate, which is also how a consent withdrawal is
     * honoured.
     *
     * <p>Refused once the person has joined. A JOINED application is the
     * evidence that a worker was lawfully recruited and vetted, which Phase 10
     * has to show the principal employer; the retention obligation outlives the
     * consent to market to them.
     */
    @Transactional
    public void delete(Long candidateId, AuthenticatedUser actor, RequestContext context) {
        Candidate candidate = load(candidateId);

        if (applicationRepository.hasJoinedApplication(candidateId)) {
            throw new BusinessRuleException(
                    "This candidate has joined, so their recruitment record has to be kept as "
                            + "evidence that the hire was vetted. Their worker record governs "
                            + "retention from here.");
        }

        candidate.markDeleted(actor.email());

        auditService.recordEntityChange(AuditAction.DELETE, "Candidate", candidateId,
                actor.id(), actor.email(),
                "Deleted candidate %s".formatted(candidate.getFullName()), null, null, context);
    }

    // ==================================================================
    // The candidate's own portal
    // ==================================================================

    @Transactional(readOnly = true)
    public CandidateDtos.SelfResponse getOwnProfile(AuthenticatedUser caller) {
        return selfWithCount(loadOwn(caller));
    }

    @Transactional
    public CandidateDtos.SelfResponse updateOwnProfile(CandidateDtos.SelfRequest request,
                                                       AuthenticatedUser caller,
                                                       RequestContext context) {
        // Creates the profile when there is none. A candidate who has just
        // registered and gone straight to "complete your profile" has no row
        // yet, and refusing them would make the message on the GET - which
        // tells them to do exactly this - a dead end.
        Candidate candidate = resolveForSelfService(caller);

        mapper.applySelfRequest(request, candidate);
        candidate.setPreferredCategory(resolveCategory(request.preferredCategoryId()));
        candidate.giveConsent();

        auditService.recordEntityChange(AuditAction.UPDATE, "Candidate", candidate.getId(),
                caller.id(), caller.email(),
                "Candidate updated their own profile", null, null, context);

        return selfWithCount(candidate);
    }

    @Transactional
    public CandidateDtos.SelfResponse uploadOwnResume(MultipartFile file,
                                                      AuthenticatedUser caller,
                                                      RequestContext context) {
        // Same as updateOwnProfile: uploading a CV is a reasonable first action
        // for a new account, and it should not need a profile to exist first.
        Candidate candidate = resolveForSelfService(caller);
        StoredDocument document = storeResume(file, candidate.getId());
        candidate.setResumeDocument(document);

        auditService.recordEntityChange(AuditAction.UPDATE, "Candidate", candidate.getId(),
                caller.id(), caller.email(),
                "Candidate uploaded their own CV '%s'".formatted(document.getOriginalFileName()),
                null, document.getStorageKey(), context);

        return selfWithCount(candidate);
    }

    /**
     * Finds or creates the candidate record for a signed-in CANDIDATE user.
     *
     * <p>A registered account does not necessarily have a candidate row yet -
     * registration creates the login, and the pipeline record appears when they
     * first apply or open their profile. If a row already exists for their
     * email from an earlier anonymous application, it is adopted rather than
     * duplicated, which is how someone who applied as a guest and then
     * registered sees that earlier application in their portal.
     */
    @Transactional
    public Candidate resolveForSelfService(AuthenticatedUser caller) {
        return candidateRepository.findActiveByUserId(caller.id())
                .orElseGet(() -> adoptOrCreateForUser(caller));
    }

    private Candidate adoptOrCreateForUser(AuthenticatedUser caller) {
        User user = userRepository.findById(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));

        if (!user.hasRole(RoleName.CANDIDATE)) {
            throw new BusinessRuleException(
                    "This account is not a candidate account, so it has no applicant profile.");
        }

        Candidate existing = candidateRepository
                .findByEmailIgnoringSoftDelete(normaliseEmail(user.getEmail()))
                .orElse(null);

        if (existing != null) {
            if (existing.getUser() != null
                    && !existing.getUser().getId().equals(user.getId())) {
                // Should be impossible: email is unique on candidates and on
                // users. Refuse rather than re-point, because guessing which
                // login owns a pipeline history is not a decision code should
                // make silently.
                throw new BusinessRuleException(
                        "This email is already linked to a different applicant profile. "
                                + "Please contact support.");
            }
            existing.setUser(user);
            existing.restore();
            log.info("Adopted existing candidate {} for newly registered user {}",
                    existing.getId(), user.getId());
            return existing;
        }

        Candidate candidate = new Candidate();
        candidate.setUser(user);
        candidate.setFullName(user.fullName());
        candidate.setEmail(normaliseEmail(user.getEmail()));
        candidate.setPhone(user.getPhone());
        candidate.setSource(CandidateSource.WEBSITE);
        // Registration already captured consent; this record exists because of it.
        candidate.giveConsent();
        return candidateRepository.save(candidate);
    }

    // ==================================================================
    // Used by ApplicationService
    // ==================================================================

    /**
     * Resolves the candidate behind an anonymous application.
     *
     * <p>Reuses an existing row for the address, and <strong>never overwrites
     * identity fields on a record that belongs to a registered user</strong>.
     * See the class note for why that second rule is not optional.
     *
     * @return the candidate to attach the application to
     */
    @Transactional
    public Candidate resolveForApplication(String email,
                                           String fullName,
                                           String phone,
                                           String city,
                                           String state,
                                           Integer totalExperienceMonths,
                                           String highestQualification,
                                           CandidateSource source) {
        String normalised = normaliseEmail(email);
        Candidate existing = candidateRepository.findByEmailIgnoringSoftDelete(normalised)
                .orElse(null);

        if (existing == null) {
            Candidate candidate = new Candidate();
            candidate.setEmail(normalised);
            candidate.setFullName(fullName);
            candidate.setPhone(phone);
            candidate.setCity(city);
            candidate.setState(state);
            candidate.setTotalExperienceMonths(totalExperienceMonths);
            candidate.setHighestQualification(highestQualification);
            candidate.setSource(source == null ? CandidateSource.WEBSITE : source);
            candidate.giveConsent();
            return candidateRepository.save(candidate);
        }

        // Re-applying undoes a soft delete: the person has asked to be
        // considered again, which is a fresh consent.
        existing.restore();
        existing.giveConsent();

        if (existing.hasLogin()) {
            // Owned by a registered user. An anonymous form must not touch any
            // of it - that is the whole point of the rule.
            log.info("Anonymous application quoted the email of registered candidate {}. "
                    + "Reusing the record without modifying it.", existing.getId());
            return existing;
        }

        // Guest-owned record: fill gaps only, so a later submission can
        // complete a sparse earlier one without erasing anything.
        if (isBlank(existing.getPhone())) {
            existing.setPhone(phone);
        }
        if (isBlank(existing.getCity())) {
            existing.setCity(city);
        }
        if (isBlank(existing.getState())) {
            existing.setState(state);
        }
        if (existing.getTotalExperienceMonths() == null) {
            existing.setTotalExperienceMonths(totalExperienceMonths);
        }
        if (isBlank(existing.getHighestQualification())) {
            existing.setHighestQualification(highestQualification);
        }
        return existing;
    }

    /** Stores a resume against a candidate, for the apply endpoints. */
    @Transactional
    public StoredDocument storeResume(MultipartFile file, Long candidateId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No file was uploaded.");
        }
        // PDF only, deliberately. A .doc resume is an OLE2 container that may
        // carry macros, and the detector identifies files by content rather
        // than extension, so "resume.pdf" containing a Word document is
        // rejected here rather than opened by whoever downloads it.
        return documentService.upload(
                file, DocumentOwnerType.CANDIDATE, candidateId, List.of(FileTypeDetector.PDF));
    }

    @Transactional(readOnly = true)
    public Candidate loadActive(Long candidateId) {
        return load(candidateId);
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private Candidate load(Long candidateId) {
        return candidateRepository.findActiveById(candidateId)
                .orElseThrow(() -> ResourceNotFoundException.of("Candidate", candidateId));
    }

    /**
     * Finds the caller's own candidate record <strong>without writing</strong>.
     *
     * <p>Falls back to matching on email, which is what lets someone who
     * applied as a guest and then registered see that earlier application the
     * first time they open the portal - before anything has linked the two
     * records.
     *
     * <p>The email fallback exists because the alternative was worse. Resolving
     * through {@link #resolveForSelfService} on a read path looked fine and was
     * silently broken: that method links the candidate to the login, the read
     * paths run in a {@code readOnly} transaction, and Spring puts Hibernate in
     * manual-flush mode there - so the link was created, never flushed, and
     * discarded at commit. The portal appeared to work while the database never
     * changed. Reads resolve; writes link.
     */
    @Transactional(readOnly = true)
    public Optional<Candidate> findOwn(AuthenticatedUser caller) {
        Optional<Candidate> byUser = candidateRepository.findActiveByUserId(caller.id());
        if (byUser.isPresent()) {
            return byUser;
        }
        return candidateRepository
                .findByEmailIgnoringSoftDelete(normaliseEmail(caller.email()))
                .filter(candidate -> !candidate.isDeleted());
    }

    private Candidate loadOwn(AuthenticatedUser caller) {
        return findOwn(caller)
                .orElseThrow(() -> new BusinessRuleException(
                        "You do not have an applicant profile yet. Save your profile or apply "
                                + "for a job to create one."));
    }

    private com.hrsolution.catalog.entity.ManpowerCategory resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", categoryId));
    }

    private CandidateDtos.Response withApplicationCount(Candidate candidate) {
        CandidateDtos.Response base = mapper.toResponse(candidate);
        long count = applicationRepository.countByCandidateIdAndDeletedFalse(candidate.getId());
        return new CandidateDtos.Response(
                base.id(), base.fullName(), base.email(), base.phone(),
                base.city(), base.state(),
                base.totalExperienceMonths(), base.experienceLabel(), base.highestQualification(),
                base.preferredCategoryId(), base.preferredCategoryName(),
                base.hasLogin(), base.userId(),
                base.resumeDocumentId(), base.resumeFileName(),
                base.source(), base.consentGiven(), base.consentGivenAt(),
                base.spamSuspected(), base.spamReason(), base.notes(),
                count, base.createdAt());
    }

    private CandidateDtos.SelfResponse selfWithCount(Candidate candidate) {
        CandidateDtos.SelfResponse base = mapper.toSelfResponse(candidate);
        long count = applicationRepository.countByCandidateIdAndDeletedFalse(candidate.getId());
        return new CandidateDtos.SelfResponse(
                base.id(), base.fullName(), base.email(), base.phone(),
                base.city(), base.state(),
                base.totalExperienceMonths(), base.experienceLabel(), base.highestQualification(),
                base.preferredCategoryId(), base.preferredCategoryName(),
                base.resumeDocumentId(), base.resumeFileName(),
                base.consentGiven(), count);
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
