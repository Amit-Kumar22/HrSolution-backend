package com.hrsolution.worker.mapper;

import com.hrsolution.worker.dto.SalaryStructureDtos;
import com.hrsolution.worker.dto.WorkerBankDtos;
import com.hrsolution.worker.dto.WorkerDocumentDtos;
import com.hrsolution.worker.dto.WorkerDtos;
import com.hrsolution.worker.entity.SalaryStructure;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.entity.WorkerBankDetail;
import com.hrsolution.worker.entity.WorkerDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Entity to DTO mapping for the worker module.
 *
 * <h2>The encrypted fields are deliberately not writable here</h2>
 *
 * <p>{@code aadhaar} and {@code pan} are {@code ignore = true} on
 * {@link #applyRequest}, and that is load-bearing rather than tidiness. Each
 * has a sibling blind-index column that must be recomputed whenever the value
 * changes. If the mapper copied the number straight onto the entity, the index
 * would silently go stale - and a stale index does not throw, it just stops
 * matching, so duplicate detection would quietly pass every re-registration of
 * the same person. {@code WorkerService} sets the pair together, which is the
 * only place that can.
 *
 * <p>Equally, no mapping produces an unmasked Aadhaar or PAN except
 * {@link #toSensitiveResponse}, which exists for the one endpoint gated on
 * {@code WORKER_SENSITIVE_READ}. Every other response carries the masked form.
 *
 * <p>As ever on a mapper in this project: no plain helper methods. MapStruct
 * turns any non-private method into a candidate type conversion for its
 * signature - see docs/decisions.md §24.
 */
@Mapper(imports = {java.time.LocalDate.class})
public interface WorkerMapper {

    // ==================================================================
    // Worker
    // ==================================================================

    @Mapping(target = "employeeCode", expression = "java(entity.employeeCode())")
    @Mapping(target = "fullName", expression = "java(entity.fullName())")
    @Mapping(target = "age", expression = "java(entity.ageOn(LocalDate.now()))")
    @Mapping(target = "serviceMonths", expression = "java(entity.serviceMonthsOn(LocalDate.now()))")
    // Masked, always. The real numbers have their own endpoint.
    @Mapping(target = "aadhaarMasked", expression = "java(entity.maskedAadhaar())")
    @Mapping(target = "panMasked", expression = "java(entity.maskedPan())")
    @Mapping(target = "aadhaarOnFile", expression = "java(entity.hasAadhaarOnFile())")
    @Mapping(target = "employed", expression = "java(entity.getStatus().isEmployed())")
    @Mapping(target = "clearedForSecurityWork",
            expression = "java(entity.getPoliceVerificationStatus()"
                    + ".permitsSecurityDeployment())")
    // Need the deployments and documents tables, so the service supplies them.
    @Mapping(target = "currentDeploymentId", ignore = true)
    @Mapping(target = "currentDeploymentLabel", ignore = true)
    @Mapping(target = "documentCount", ignore = true)
    @Mapping(target = "documentsPendingVerification", ignore = true)
    WorkerDtos.Response toResponse(Worker entity);

    @Mapping(target = "employeeCode", expression = "java(entity.employeeCode())")
    @Mapping(target = "fullName", expression = "java(entity.fullName())")
    @Mapping(target = "currentDeploymentLabel", ignore = true)
    WorkerDtos.SummaryResponse toSummaryResponse(Worker entity);

    /**
     * The only mapping that exposes the real identifiers.
     *
     * <p>Called from one place, behind a {@code WORKER_SENSITIVE_READ} check
     * and an audit write. If you are adding a second caller, that is the moment
     * to ask whether it needs the numbers or just the masked form.
     */
    @Mapping(target = "employeeCode", expression = "java(entity.employeeCode())")
    @Mapping(target = "fullName", expression = "java(entity.fullName())")
    WorkerDtos.SensitiveResponse toSensitiveResponse(Worker entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "candidate", ignore = true)
    // Encrypted pair - see the class note. Set by WorkerService with the hash.
    @Mapping(target = "aadhaar", ignore = true)
    @Mapping(target = "aadhaarHash", ignore = true)
    @Mapping(target = "pan", ignore = true)
    @Mapping(target = "panHash", ignore = true)
    // Each has its own endpoint and audit event.
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "policeVerificationStatus", ignore = true)
    @Mapping(target = "policeVerificationDate", ignore = true)
    @Mapping(target = "lastWorkingDay", ignore = true)
    @Mapping(target = "exitReason", ignore = true)
    @Mapping(target = "fullAndFinalSettled", ignore = true)
    void applyRequest(WorkerDtos.Request request, @MappingTarget Worker entity);

    // ==================================================================
    // Bank accounts
    // ==================================================================

    @Mapping(target = "workerId", expression = "java(entity.getWorker().getId())")
    @Mapping(target = "accountNumberMasked", expression = "java(entity.maskedAccountNumber())")
    @Mapping(target = "displayLabel", expression = "java(entity.displayLabel())")
    WorkerBankDtos.Response toResponse(WorkerBankDetail entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "worker", ignore = true)
    // Encrypted pair, as above.
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "accountNumberHash", ignore = true)
    // "Exactly one primary" is a cross-row rule, so the service owns it.
    @Mapping(target = "primaryAccount", ignore = true)
    @Mapping(target = "active", ignore = true)
    void applyRequest(WorkerBankDtos.Request request, @MappingTarget WorkerBankDetail entity);

    // ==================================================================
    // Documents
    // ==================================================================

    @Mapping(target = "workerId", expression = "java(entity.getWorker().getId())")
    @Mapping(target = "documentTypeLabel",
            expression = "java(entity.getDocumentType().getDisplayName())")
    @Mapping(target = "documentId", expression = "java(entity.getDocument().getId())")
    @Mapping(target = "fileName",
            expression = "java(entity.getDocument().getOriginalFileName())")
    @Mapping(target = "sizeBytes", expression = "java(entity.getDocument().getSizeBytes())")
    @Mapping(target = "verifiedByUserId",
            expression = "java(entity.getVerifiedBy() == null ? null "
                    + ": entity.getVerifiedBy().getId())")
    @Mapping(target = "verifiedByName",
            expression = "java(entity.getVerifiedBy() == null ? null "
                    + ": entity.getVerifiedBy().fullName())")
    @Mapping(target = "expired", expression = "java(entity.hasExpiredOn(LocalDate.now()))")
    @Mapping(target = "daysUntilExpiry",
            expression = "java(entity.daysUntilExpiry(LocalDate.now()))")
    @Mapping(target = "validEvidence",
            expression = "java(entity.isValidEvidenceOn(LocalDate.now()))")
    @Mapping(target = "missingExpectedExpiry",
            expression = "java(entity.isMissingExpectedExpiry())")
    WorkerDocumentDtos.Response toResponse(WorkerDocument entity);

    // ==================================================================
    // Salary structures
    // ==================================================================

    @Mapping(target = "workerId", expression = "java(entity.getWorker().getId())")
    @Mapping(target = "workerName", expression = "java(entity.getWorker().fullName())")
    @Mapping(target = "gross", expression = "java(entity.gross())")
    @Mapping(target = "providentFundBase", expression = "java(entity.providentFundBase())")
    @Mapping(target = "current", expression = "java(entity.isCurrent())")
    SalaryStructureDtos.Response toResponse(SalaryStructure entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "worker", ignore = true)
    // Closed by the service when a successor supersedes this one.
    @Mapping(target = "effectiveTo", ignore = true)
    void applyRequest(SalaryStructureDtos.Request request, @MappingTarget SalaryStructure entity);
}
