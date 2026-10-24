package com.hrsolution.worker.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.crypto.FieldCrypto;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.deployment.repository.DeploymentRepository;
import com.hrsolution.recruitment.entity.Application;
import com.hrsolution.recruitment.entity.Candidate;
import com.hrsolution.recruitment.repository.ApplicationRepository;
import com.hrsolution.worker.dto.WorkerDtos;
import com.hrsolution.worker.entity.DocumentVerificationStatus;
import com.hrsolution.worker.entity.PoliceVerificationStatus;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.entity.WorkerStatus;
import com.hrsolution.worker.mapper.WorkerMapper;
import com.hrsolution.worker.repository.WorkerDocumentRepository;
import com.hrsolution.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Workers - the people on the payroll.
 *
 * <h2>The encrypted pair</h2>
 *
 * <p>Aadhaar and PAN each live in two columns: the ciphertext, and an HMAC
 * blind index for lookups. {@link #applyEncryptedIdentifiers} is the only place
 * that writes either, and it always writes both. That is not a convention worth
 * relaxing - the ciphertext uses a random IV, so a stale index does not throw,
 * it simply stops matching, and duplicate detection would quietly start
 * admitting the same person twice. The mapper refuses to touch these fields for
 * exactly that reason.
 *
 * <h2>Reading the real numbers</h2>
 *
 * <p>{@link #getSensitive} is the only path that returns an unmasked
 * identifier. It is gated on {@code WORKER_SENSITIVE_READ} at the controller
 * and writes an audit row on every call, because "who looked up whose Aadhaar"
 * is a question a DPDP audit will actually ask. Everything else returns the
 * masked form.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository documentRepository;
    private final DeploymentRepository deploymentRepository;
    private final ApplicationRepository applicationRepository;
    private final FieldCrypto fieldCrypto;
    private final WorkerMapper mapper;
    private final AuditService auditService;

    // ==================================================================
    // Reading
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<WorkerDtos.SummaryResponse> list(String search,
                                                         WorkerStatus status,
                                                         PoliceVerificationStatus verification,
                                                         Pageable pageable) {
        Specification<Worker> specification = Specification.<Worker>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("policeVerificationStatus", verification))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "firstName", "lastName", "phone", "email", "uan"));

        Page<Worker> page = workerRepository.findAll(specification, pageable);

        return PageResponse.from(page, worker -> {
            WorkerDtos.SummaryResponse base = mapper.toSummaryResponse(worker);
            return new WorkerDtos.SummaryResponse(
                    base.id(), base.employeeCode(), base.fullName(), base.phone(),
                    base.status(), base.policeVerificationStatus(), base.dateOfJoining(),
                    currentDeployment(worker.getId())
                            .map(Deployment::displayLabel)
                            .orElse(null));
        });
    }

    @Transactional(readOnly = true)
    public WorkerDtos.Response get(Long workerId) {
        return withDerived(load(workerId));
    }

    /**
     * Finds a worker by Aadhaar, for the "is this person already on file"
     * check at a walk-in desk.
     *
     * <p>Takes the number and hashes it here rather than accepting a hash, so
     * no caller ever has to know how the index is computed. The number itself
     * is not logged.
     */
    @Transactional(readOnly = true)
    public Optional<WorkerDtos.SummaryResponse> findByAadhaar(String aadhaar) {
        String hash = fieldCrypto.blindIndex(aadhaar);
        if (hash == null) {
            return Optional.empty();
        }
        return workerRepository.findByAadhaarHashIgnoringSoftDelete(hash)
                .filter(worker -> !worker.isDeleted())
                .map(mapper::toSummaryResponse);
    }

    /**
     * The unmasked identifiers.
     *
     * <p>Audited on every call. The PF and ESI returns and the bank transfer
     * file all need the real numbers, so this cannot simply not exist - what it
     * can do is leave a trail.
     */
    @Transactional
    public WorkerDtos.SensitiveResponse getSensitive(Long workerId,
                                                     AuthenticatedUser caller,
                                                     RequestContext context) {
        Worker worker = load(workerId);

        auditService.recordSecurityEvent(AuditAction.SENSITIVE_DATA_ACCESSED,
                caller.id(), caller.email(), true,
                "Viewed the unmasked Aadhaar and PAN of worker %s (%s)".formatted(
                        worker.employeeCode(), worker.fullName()),
                context);

        return mapper.toSensitiveResponse(worker);
    }

    // ==================================================================
    // Writing
    // ==================================================================

    @Transactional
    public WorkerDtos.Response create(WorkerDtos.Request request,
                                      AuthenticatedUser actor,
                                      RequestContext context) {
        validateAge(request.dateOfBirth(), request.dateOfJoining());

        Worker worker = new Worker();
        mapper.applyRequest(request, worker);
        applyEncryptedIdentifiers(worker, request.aadhaar(), request.pan(), null);
        worker.setStatus(WorkerStatus.ON_BENCH);

        requireUanAvailable(request.uan(), null);

        Worker saved = workerRepository.saveAndFlush(worker);

        auditService.recordEntityChange(AuditAction.CREATE, "Worker", saved.getId(),
                actor.id(), actor.email(),
                "Onboarded %s (%s), joining %s".formatted(
                        saved.employeeCode(), saved.fullName(), saved.getDateOfJoining()),
                null, saved.employeeCode(), context);

        return withDerived(saved);
    }

    @Transactional
    public WorkerDtos.Response update(Long workerId,
                                      WorkerDtos.Request request,
                                      AuthenticatedUser actor,
                                      RequestContext context) {
        Worker worker = load(workerId);
        validateAge(request.dateOfBirth(), request.dateOfJoining());
        requireUanAvailable(request.uan(), workerId);

        mapper.applyRequest(request, worker);
        applyEncryptedIdentifiers(worker, request.aadhaar(), request.pan(), workerId);

        auditService.recordEntityChange(AuditAction.UPDATE, "Worker", workerId,
                actor.id(), actor.email(),
                "Updated worker " + worker.employeeCode(), null, null, context);

        return withDerived(worker);
    }

    /**
     * Records the outcome of a police verification.
     *
     * <p>Its own endpoint rather than a field on the update: it gates whether
     * the worker may be posted to a security role at all, so it needs its own
     * audit row and should not be changeable by a casual profile edit.
     */
    @Transactional
    public WorkerDtos.Response recordPoliceVerification(
            Long workerId,
            WorkerDtos.PoliceVerificationRequest request,
            AuthenticatedUser actor,
            RequestContext context) {
        Worker worker = load(workerId);
        PoliceVerificationStatus previous = worker.getPoliceVerificationStatus();

        worker.setPoliceVerificationStatus(request.status());
        worker.setPoliceVerificationDate(request.verifiedOn());

        if (request.status() == PoliceVerificationStatus.FAILED) {
            List<Deployment> active = deploymentRepository.findActiveForWorker(workerId);
            if (!active.isEmpty()) {
                // Not auto-released: pulling someone off a site is an operational
                // decision with a client conversation attached. But it cannot be
                // silent - under the PSAR Act this person should not be there.
                log.warn("Worker {} failed police verification while holding {} active "
                                + "deployment(s). They must be released from any security "
                                + "posting.", worker.employeeCode(), active.size());
            }
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Worker", workerId,
                actor.id(), actor.email(),
                "Police verification of %s moved %s -> %s".formatted(
                        worker.employeeCode(), previous, request.status()),
                previous.name(), request.status().name(), context);

        return withDerived(worker);
    }

    /**
     * Records a worker leaving.
     *
     * <p>Refuses while they still hold an active posting. A worker whose last
     * working day has passed but who is still deployed would keep appearing on
     * the client's attendance sheet in Phase 7 and on their invoice in Phase 9.
     */
    @Transactional
    public WorkerDtos.Response recordExit(Long workerId,
                                          WorkerDtos.ExitRequest request,
                                          AuthenticatedUser actor,
                                          RequestContext context) {
        Worker worker = load(workerId);

        if (request.status() != WorkerStatus.RESIGNED
                && request.status() != WorkerStatus.TERMINATED) {
            throw new BusinessRuleException(
                    "An exit must be recorded as RESIGNED or TERMINATED.");
        }
        if (worker.getStatus().hasExited()) {
            throw new BusinessRuleException(
                    ("This worker already left on %s (%s).").formatted(
                            worker.getLastWorkingDay(), worker.getStatus()));
        }

        List<Deployment> active = deploymentRepository.findActiveForWorker(workerId);
        if (!active.isEmpty()) {
            Deployment first = active.getFirst();
            throw new BusinessRuleException(
                    ("This worker is still deployed at %s until %s. End the deployment first, "
                            + "or they will keep appearing on that client's attendance sheet "
                            + "and invoice.").formatted(
                                    first.displayLabel(),
                                    first.getEndDate() == null ? "further notice"
                                            : first.getEndDate()));
        }

        if (request.lastWorkingDay().isBefore(worker.getDateOfJoining())) {
            throw new BusinessRuleException(
                    ("The last working day is before the joining date of %s.")
                            .formatted(worker.getDateOfJoining()));
        }

        worker.recordExit(request.status(), request.lastWorkingDay(), request.reason());

        auditService.recordEntityChange(AuditAction.UPDATE, "Worker", workerId,
                actor.id(), actor.email(),
                "%s %s on %s: %s".formatted(
                        request.status() == WorkerStatus.RESIGNED ? "Resignation of" : "Dismissal of",
                        worker.employeeCode(), request.lastWorkingDay(), request.reason()),
                null, request.status().name(), context);

        return withDerived(worker);
    }

    /** Marks full and final settlement done, which Phase 8 will set automatically. */
    @Transactional
    public WorkerDtos.Response markSettled(Long workerId,
                                           AuthenticatedUser actor,
                                           RequestContext context) {
        Worker worker = load(workerId);

        if (!worker.getStatus().hasExited()) {
            throw new BusinessRuleException(
                    "Full and final settlement applies to a worker who has left. Record their "
                            + "exit first.");
        }

        worker.setFullAndFinalSettled(true);

        auditService.recordEntityChange(AuditAction.UPDATE, "Worker", workerId,
                actor.id(), actor.email(),
                "Full and final settlement recorded for " + worker.employeeCode(),
                null, "true", context);

        return withDerived(worker);
    }

    /**
     * Converts a {@code JOINED} application into a worker.
     *
     * <p>The link Phase 5 logged and left for this phase. Requires the
     * application to actually be {@code JOINED} - converting a shortlisted
     * candidate would put somebody on a client site with no record of a hiring
     * decision, which is the thing the funnel's state machine exists to
     * prevent.
     */
    @Transactional
    public WorkerDtos.Response createFromApplication(
            Long applicationId,
            WorkerDtos.FromApplicationRequest request,
            AuthenticatedUser actor,
            RequestContext context) {
        Application application = applicationRepository.findActiveById(applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));

        if (!application.getStatus().isHired()) {
            throw new BusinessRuleException(
                    ("This application is %s, not JOINED, so there is no hire to convert. Move "
                            + "it to JOINED once the person actually starts.")
                            .formatted(application.getStatus()));
        }

        Candidate candidate = application.getCandidate();
        workerRepository.findActiveByCandidateId(candidate.getId()).ifPresent(existing -> {
            throw new DuplicateResourceException(
                    ("%s has already been converted into worker %s.").formatted(
                            candidate.getFullName(), existing.employeeCode()));
        });

        validateAge(request.dateOfBirth(), request.dateOfJoining());

        Worker worker = new Worker();
        // Carried across from the candidate record so the same person is not
        // re-typed, and so the worker is traceable back to their application.
        worker.setCandidate(candidate);
        worker.setUser(candidate.getUser());
        worker.setFirstName(firstNameOf(candidate.getFullName()));
        worker.setLastName(lastNameOf(candidate.getFullName()));
        worker.setEmail(candidate.getEmail());
        worker.setPhone(candidate.getPhone());
        worker.setCity(candidate.getCity());
        worker.setState(candidate.getState());
        worker.setFatherOrHusbandName(request.fatherOrHusbandName());
        worker.setDateOfBirth(request.dateOfBirth());
        worker.setGender(request.gender());
        worker.setDateOfJoining(request.dateOfJoining());
        worker.setStatus(WorkerStatus.ON_BENCH);
        applyEncryptedIdentifiers(worker, request.aadhaar(), request.pan(), null);

        Worker saved = workerRepository.saveAndFlush(worker);

        auditService.recordEntityChange(AuditAction.CREATE, "Worker", saved.getId(),
                actor.id(), actor.email(),
                "Converted application %s into worker %s (%s)".formatted(
                        com.hrsolution.recruitment.ApplicationReference.of(applicationId),
                        saved.employeeCode(), saved.fullName()),
                null, saved.employeeCode(), context);

        return withDerived(saved);
    }

    @Transactional
    public void delete(Long workerId, AuthenticatedUser actor, RequestContext context) {
        Worker worker = load(workerId);

        List<Deployment> history = deploymentRepository.findHistoryForWorker(workerId);
        if (!history.isEmpty()) {
            throw new BusinessRuleException(
                    ("This worker has %d deployment(s) on record, so deleting them would orphan "
                            + "the attendance and billing history. Record their exit instead.")
                            .formatted(history.size()));
        }

        worker.markDeleted(actor.email());

        auditService.recordEntityChange(AuditAction.DELETE, "Worker", workerId,
                actor.id(), actor.email(),
                "Deleted worker " + worker.employeeCode(), null, null, context);
    }

    // ==================================================================
    // Used by other services in this phase
    // ==================================================================

    @Transactional(readOnly = true)
    public Worker loadActive(Long workerId) {
        return load(workerId);
    }

    /** Projects the worker's status from their postings. Called by DeploymentService. */
    @Transactional
    public void refreshDeploymentStatus(Worker worker) {
        if (!worker.getStatus().isDerivedFromDeployments()) {
            // ON_LEAVE, RESIGNED and TERMINATED are not ours to overwrite.
            return;
        }
        boolean deployed = !deploymentRepository.findActiveForWorker(worker.getId()).isEmpty();
        worker.setStatus(deployed ? WorkerStatus.DEPLOYED : WorkerStatus.ON_BENCH);
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private Worker load(Long workerId) {
        return workerRepository.findActiveById(workerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Worker", workerId));
    }

    /**
     * Writes an encrypted identifier and its blind index together, refusing a
     * value already held by someone else.
     *
     * <p>The pairing is the point - see the class note. The duplicate check
     * runs on the hash because the ciphertext cannot be compared, and the error
     * names the existing worker's code rather than echoing the number back.
     *
     * @param excludeWorkerId the worker being updated, so their own stored
     *                        value is not reported as a clash with itself
     */
    private void applyEncryptedIdentifiers(Worker worker, String aadhaar, String pan,
                                           Long excludeWorkerId) {
        String aadhaarHash = fieldCrypto.blindIndex(aadhaar);
        if (aadhaarHash != null) {
            workerRepository.findByAadhaarHashIgnoringSoftDelete(aadhaarHash)
                    .filter(other -> !other.getId().equals(excludeWorkerId))
                    .ifPresent(other -> {
                        throw new DuplicateResourceException(
                                ("This Aadhaar is already on file for worker %s (%s). The "
                                        + "statutory registers assume one record per person, so "
                                        + "update that record instead of creating a second one.")
                                        .formatted(other.employeeCode(), other.fullName()));
                    });
        }
        worker.setAadhaar(normalise(aadhaar));
        worker.setAadhaarHash(aadhaarHash);

        String panHash = fieldCrypto.blindIndex(pan);
        if (panHash != null) {
            workerRepository.findByPanHashIgnoringSoftDelete(panHash)
                    .filter(other -> !other.getId().equals(excludeWorkerId))
                    .ifPresent(other -> {
                        throw new DuplicateResourceException(
                                ("This PAN is already on file for worker %s (%s).")
                                        .formatted(other.employeeCode(), other.fullName()));
                    });
        }
        worker.setPan(normalise(pan));
        worker.setPanHash(panHash);
    }

    private void requireUanAvailable(String uan, Long excludeWorkerId) {
        if (uan == null || uan.isBlank()) {
            return;
        }
        workerRepository.findByUanIgnoringSoftDelete(uan.trim())
                .filter(other -> !other.getId().equals(excludeWorkerId))
                .ifPresent(other -> {
                    throw new DuplicateResourceException(
                            ("This UAN is already on file for worker %s. A universal account "
                                    + "number belongs to one person for life, so this is either "
                                    + "a typo or the same person twice.")
                                    .formatted(other.employeeCode()));
                });
    }

    /**
     * Refuses to employ anyone under 18 on their joining date.
     *
     * <p>14 is the floor under the Child Labour (Prohibition and Regulation)
     * Act, but the work these placements involve - security, factory
     * operations, mines - is hazardous and prohibited below 18. Storing the
     * record would make the company's own register the evidence against it.
     */
    private void validateAge(LocalDate dateOfBirth, LocalDate dateOfJoining) {
        if (dateOfBirth == null || dateOfJoining == null) {
            return;
        }
        if (dateOfBirth.isAfter(dateOfJoining)) {
            throw new BusinessRuleException(
                    "The date of birth is after the joining date.");
        }
        int ageAtJoining = java.time.Period.between(dateOfBirth, dateOfJoining).getYears();
        if (ageAtJoining < Worker.MINIMUM_WORKING_AGE) {
            throw new BusinessRuleException(
                    ("This person would be %d on their joining date. The work these placements "
                            + "involve is prohibited below %d under the Child Labour "
                            + "(Prohibition and Regulation) Act.")
                            .formatted(ageAtJoining, Worker.MINIMUM_WORKING_AGE));
        }
    }

    private Optional<Deployment> currentDeployment(Long workerId) {
        LocalDate today = LocalDate.now();
        return deploymentRepository.findActiveForWorker(workerId).stream()
                .filter(deployment -> deployment.isInForceOn(today))
                .findFirst();
    }

    private WorkerDtos.Response withDerived(Worker worker) {
        WorkerDtos.Response base = mapper.toResponse(worker);
        Optional<Deployment> current = currentDeployment(worker.getId());

        List<com.hrsolution.worker.entity.WorkerDocument> documents =
                documentRepository.findForWorker(worker.getId());
        int pending = (int) documents.stream()
                .filter(document -> document.getVerificationStatus()
                        == DocumentVerificationStatus.PENDING)
                .count();

        return new WorkerDtos.Response(
                base.id(), base.employeeCode(),
                base.firstName(), base.lastName(), base.fullName(), base.fatherOrHusbandName(),
                base.dateOfBirth(), base.age(), base.gender(), base.dateOfJoining(),
                base.serviceMonths(),
                base.phone(), base.email(),
                base.addressLine1(), base.addressLine2(), base.city(), base.state(),
                base.pincode(),
                base.aadhaarMasked(), base.panMasked(), base.aadhaarOnFile(),
                base.uan(), base.esicIpNumber(),
                base.status(), base.employed(),
                base.policeVerificationStatus(), base.policeVerificationDate(),
                base.clearedForSecurityWork(),
                base.emergencyContactName(), base.emergencyContactPhone(),
                base.emergencyContactRelation(),
                base.lastWorkingDay(), base.exitReason(), base.fullAndFinalSettled(),
                current.map(Deployment::getId).orElse(null),
                current.map(Deployment::displayLabel).orElse(null),
                documents.size(), pending,
                base.notes(), base.createdAt());
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Unknown";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private static String lastNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return null;
        }
        return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }
}
