package com.hrsolution.worker.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.storage.FileTypeDetector;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.document.service.DocumentService;
import com.hrsolution.user.repository.UserRepository;
import com.hrsolution.worker.dto.WorkerDocumentDtos;
import com.hrsolution.worker.entity.DocumentVerificationStatus;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.entity.WorkerDocument;
import com.hrsolution.worker.entity.WorkerDocumentType;
import com.hrsolution.worker.mapper.WorkerMapper;
import com.hrsolution.worker.repository.WorkerDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Worker documents - the evidence file.
 *
 * <p>The file goes through Phase 3's {@code DocumentService}, so storage,
 * checksums, content-type detection by magic bytes and the authenticated
 * download endpoint are reused rather than rebuilt. This service owns what the
 * file means.
 *
 * <p>{@link #latestValidEvidence} is the method Phase 6's deployment gate and
 * Phase 10's compliance reports both call. Note what it requires: verified
 * <em>and</em> unexpired. An unverified document proves nothing - anyone can
 * upload a photograph - and an expired one proved something once and no longer
 * does.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerDocumentService {

    private final WorkerDocumentRepository documentRepository;
    private final WorkerService workerService;
    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final WorkerMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WorkerDocumentDtos.Response> listForWorker(Long workerId) {
        workerService.loadActive(workerId);
        return documentRepository.findForWorker(workerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkerDocumentDtos.Response get(Long workerDocumentId) {
        return mapper.toResponse(load(workerDocumentId));
    }

    @Transactional
    public WorkerDocumentDtos.Response upload(Long workerId,
                                              WorkerDocumentDtos.UploadRequest request,
                                              MultipartFile file,
                                              AuthenticatedUser actor,
                                              RequestContext context) {
        Worker worker = workerService.loadActive(workerId);

        if (request.issuedOn() != null && request.expiresOn() != null
                && request.expiresOn().isBefore(request.issuedOn())) {
            throw new BusinessRuleException(
                    "The expiry date is before the issue date.");
        }
        if (request.expiresOn() != null && request.expiresOn().isBefore(LocalDate.now())) {
            // Allowed: the historical record of a lapsed certificate is worth
            // keeping, and refusing it would force staff to fake the date.
            log.info("Document of type {} uploaded for worker {} is already expired ({}).",
                    request.documentType(), worker.employeeCode(), request.expiresOn());
        }

        // PDFs and images only. A worker document is a scan or a photograph;
        // accepting an Office file would mean storing macro-capable containers
        // in a folder people download from.
        StoredDocument stored = documentService.upload(
                file, DocumentOwnerType.WORKER, workerId,
                List.of(FileTypeDetector.PDF, FileTypeDetector.PNG, FileTypeDetector.JPEG));

        WorkerDocument document = new WorkerDocument();
        document.setWorker(worker);
        document.setDocument(stored);
        document.setDocumentType(request.documentType());
        document.setReferenceNumber(request.referenceNumber());
        document.setIssuedOn(request.issuedOn());
        document.setExpiresOn(request.expiresOn());
        document.setVerificationStatus(DocumentVerificationStatus.PENDING);

        WorkerDocument saved = documentRepository.save(document);

        if (saved.isMissingExpectedExpiry()) {
            log.warn("Document {} of type {} for worker {} has no expiry date, but that type "
                            + "normally renews. The Phase 10 reminders will not cover it.",
                    saved.getId(), saved.getDocumentType(), worker.employeeCode());
        }

        auditService.recordEntityChange(AuditAction.CREATE, "WorkerDocument", saved.getId(),
                actor.id(), actor.email(),
                "Uploaded %s for %s".formatted(
                        saved.getDocumentType().getDisplayName(), worker.employeeCode()),
                null, stored.getStorageKey(), context);

        return mapper.toResponse(saved);
    }

    /**
     * Records a verification decision.
     *
     * <p>Write-once in effect: a decided document cannot be re-decided, because
     * the decision is the evidence. Re-upload the document if it needs looking
     * at again, which also keeps the superseded version on file.
     */
    @Transactional
    public WorkerDocumentDtos.Response verify(Long workerDocumentId,
                                              WorkerDocumentDtos.VerificationRequest request,
                                              AuthenticatedUser actor,
                                              RequestContext context) {
        WorkerDocument document = load(workerDocumentId);

        if (request.status() == DocumentVerificationStatus.PENDING) {
            throw new BusinessRuleException(
                    "PENDING is the starting state, not a decision. Record VERIFIED or "
                            + "REJECTED.");
        }
        if (document.getVerificationStatus().isDecided()) {
            throw new BusinessRuleException(
                    ("This document was already recorded as %s by %s. Upload a replacement if "
                            + "it needs looking at again.").formatted(
                                    document.getVerificationStatus(),
                                    document.getVerifiedBy() == null ? "staff"
                                            : document.getVerifiedBy().getEmail()));
        }
        if (request.status() == DocumentVerificationStatus.REJECTED
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessRuleException(
                    "A reason is required when rejecting a document. Whoever has to obtain a "
                            + "replacement needs to know what was wrong with this one.");
        }

        document.recordVerification(request.status(),
                userRepository.findById(actor.id()).orElse(null),
                request.reason());

        auditService.recordEntityChange(AuditAction.UPDATE, "WorkerDocument", workerDocumentId,
                actor.id(), actor.email(),
                "%s %s for %s".formatted(
                        request.status() == DocumentVerificationStatus.VERIFIED
                                ? "Verified" : "Rejected",
                        document.getDocumentType().getDisplayName(),
                        document.getWorker().employeeCode()),
                null, request.status().name(), context);

        return mapper.toResponse(document);
    }

    @Transactional
    public void delete(Long workerDocumentId, AuthenticatedUser actor, RequestContext context) {
        WorkerDocument document = load(workerDocumentId);

        if (document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new BusinessRuleException(
                    "A verified document is compliance evidence and cannot be removed. Upload a "
                            + "replacement instead - both stay on file.");
        }

        document.markDeleted(actor.email());

        auditService.recordEntityChange(AuditAction.DELETE, "WorkerDocument", workerDocumentId,
                actor.id(), actor.email(),
                "Removed %s from %s".formatted(
                        document.getDocumentType().getDisplayName(),
                        document.getWorker().employeeCode()),
                null, null, context);
    }

    /** Documents lapsing within the window, for the Phase 10 reminders. */
    @Transactional(readOnly = true)
    public List<WorkerDocumentDtos.Response> listExpiring(int withinDays) {
        return documentRepository.findExpiringBy(LocalDate.now().plusDays(withinDays)).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * The latest document of a type that is verified and unexpired.
     *
     * <p>The deployment gate calls this for {@code POLICE_VERIFICATION}. Empty
     * means "no valid evidence on file", which is not the same as "no document
     * on file" - a rejected or lapsed certificate is present but worthless.
     */
    @Transactional(readOnly = true)
    public Optional<WorkerDocument> latestValidEvidence(Long workerId,
                                                        WorkerDocumentType type,
                                                        LocalDate asOf) {
        return documentRepository.findLatestOfType(workerId, type)
                .filter(document -> document.isValidEvidenceOn(asOf));
    }

    private WorkerDocument load(Long workerDocumentId) {
        return documentRepository.findActiveById(workerDocumentId)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Worker document", workerDocumentId));
    }
}
