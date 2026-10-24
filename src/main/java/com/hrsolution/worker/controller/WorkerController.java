package com.hrsolution.worker.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.dto.DeploymentDtos;
import com.hrsolution.deployment.service.DeploymentService;
import com.hrsolution.worker.dto.SalaryStructureDtos;
import com.hrsolution.worker.dto.WorkerDtos;
import com.hrsolution.worker.entity.PoliceVerificationStatus;
import com.hrsolution.worker.entity.WorkerStatus;
import com.hrsolution.worker.service.SalaryStructureService;
import com.hrsolution.worker.service.WorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Workers.
 *
 * <p><strong>Aadhaar and PAN are masked on every response here.</strong> The
 * real numbers come from {@code GET /workers/{id}/sensitive} alone, which needs
 * {@code WORKER_SENSITIVE_READ} - held by SUPER_ADMIN, ADMIN and ACCOUNTS - and
 * writes an audit row on every call. Build the UI against the masked values and
 * put the unmasked view behind a deliberate click.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/workers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Workers", description = "The workforce supplied to client sites")
public class WorkerController {

    private final WorkerService workerService;
    private final SalaryStructureService salaryStructureService;
    private final DeploymentService deploymentService;

    // ==================================================================
    // Reading
    // ==================================================================

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_READ + "')")
    @Operation(summary = "List workers",
            description = "Filter by status for the bench list (ON_BENCH), or by "
                    + "policeVerification for the compliance chase list.")
    public PageResponse<WorkerDtos.SummaryResponse> list(
            @Parameter(description = "Matches name, phone, email or UAN")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkerStatus status,
            @RequestParam(required = false) PoliceVerificationStatus policeVerification,
            @ParameterObject
            @PageableDefault(size = 20, sort = "firstName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return workerService.list(search, status, policeVerification, pageable);
    }

    @GetMapping("/{workerId}")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_READ + "')")
    @Operation(summary = "Get one worker, with Aadhaar and PAN masked")
    public WorkerDtos.Response get(@PathVariable Long workerId) {
        return workerService.get(workerId);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_READ + "')")
    @Operation(summary = "Find a worker by Aadhaar",
            description = """
                    For the walk-in desk: "have we onboarded this person before?"

                    Send the Aadhaar number; it is hashed server-side and matched against the
                    blind index. The number itself is never logged, and the response is the
                    normal masked summary.

                    204 when nobody matches - not 404, because the request was valid and the
                    answer is simply "no".""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching worker"),
            @ApiResponse(responseCode = "204", description = "No worker holds that Aadhaar")
    })
    public ResponseEntity<WorkerDtos.SummaryResponse> lookupByAadhaar(
            @Parameter(description = "12-digit Aadhaar", example = "234512341234")
            @RequestParam String aadhaar) {
        return workerService.findByAadhaar(aadhaar)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{workerId}/sensitive")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_SENSITIVE_READ + "')")
    @Operation(summary = "Get a worker's unmasked Aadhaar and PAN",
            description = """
                    The only endpoint that returns them in full. Needed because the PF and ESI
                    returns require the real numbers, so masking everywhere would make the
                    statutory filings impossible.

                    **Every call writes a `SENSITIVE_DATA_ACCESSED` row to the audit trail**,
                    naming the caller and the worker. "Who looked up whose Aadhaar" is a
                    question a DPDP audit asks directly.

                    Held by SUPER_ADMIN, ADMIN and ACCOUNTS.""")
    public WorkerDtos.SensitiveResponse getSensitive(
            @PathVariable Long workerId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return workerService.getSensitive(workerId, caller, RequestContext.from(httpRequest));
    }

    @GetMapping("/{workerId}/deployments")
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_READ + "')")
    @Operation(summary = "A worker's full posting history, newest first")
    public List<DeploymentDtos.Response> deploymentHistory(@PathVariable Long workerId) {
        return deploymentService.historyForWorker(workerId);
    }

    // ==================================================================
    // Writing
    // ==================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Onboard a worker",
            description = """
                    Created as `ON_BENCH`; the status becomes `DEPLOYED` automatically when a
                    posting is created.

                    Aadhaar and PAN are encrypted at rest and returned masked. A second worker
                    with the same Aadhaar, PAN or UAN is rejected with 409 - the statutory
                    registers assume one record per person.

                    `employeeCode` is derived from the joining year and the id, so it is not
                    something you send.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Onboarded"),
            @ApiResponse(responseCode = "409",
                    description = "That Aadhaar, PAN or UAN is already on file, naming the "
                            + "existing worker (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "Under 18 on the joining date, or the date of birth is after "
                            + "it (BUSINESS_RULE_VIOLATION)")
    })
    public WorkerDtos.Response create(@Valid @RequestBody WorkerDtos.Request request,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        return workerService.create(request, caller, RequestContext.from(httpRequest));
    }

    @PostMapping("/from-application/{applicationId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Convert a JOINED application into a worker",
            description = """
                    Carries the name, contact details and candidate link across, so the same
                    person is not re-typed and the worker stays traceable to the application
                    they were hired from - which is what Phase 10 shows a principal employer
                    asking whether a hire was vetted.

                    Requires the application to actually be `JOINED`. Converting a shortlisted
                    candidate would put someone on a client site with no record of a hiring
                    decision.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Converted"),
            @ApiResponse(responseCode = "409",
                    description = "That candidate is already a worker (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "The application is not JOINED (BUSINESS_RULE_VIOLATION)")
    })
    public WorkerDtos.Response createFromApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody WorkerDtos.FromApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return workerService.createFromApplication(
                applicationId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{workerId}")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Update a worker",
            description = "Sending an Aadhaar or PAN replaces the stored one and its search "
                    + "index together. Omitting them clears them.")
    public WorkerDtos.Response update(@PathVariable Long workerId,
                                      @Valid @RequestBody WorkerDtos.Request request,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        return workerService.update(
                workerId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{workerId}/police-verification")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Record a police verification outcome",
            description = """
                    Its own endpoint because it gates deployment to security roles: under the
                    Private Security Agencies (Regulation) Act a guard may not be posted
                    without a clear verification.

                    `VERIFIED` and `NOT_REQUIRED` permit it; `FAILED` blocks it permanently.
                    Upload the certificate separately with
                    `POST /workers/{id}/documents` - the status is data entry, the document is
                    the evidence.""")
    public WorkerDtos.Response recordPoliceVerification(
            @PathVariable Long workerId,
            @Valid @RequestBody WorkerDtos.PoliceVerificationRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return workerService.recordPoliceVerification(
                workerId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{workerId}/exit")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Record a worker leaving",
            description = "RESIGNED or TERMINATED. Refused while they still hold an active "
                    + "posting - otherwise they keep appearing on that client's attendance "
                    + "sheet and invoice. End the deployment first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated worker"),
            @ApiResponse(responseCode = "422",
                    description = "Still deployed, already left, or the last working day is "
                            + "before the joining date (BUSINESS_RULE_VIOLATION)")
    })
    public WorkerDtos.Response recordExit(@PathVariable Long workerId,
                                          @Valid @RequestBody WorkerDtos.ExitRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest httpRequest) {
        return workerService.recordExit(
                workerId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{workerId}/settled")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Mark full and final settlement done",
            description = "Phase 8 will set this automatically when the final payrun clears. "
                    + "Until then it is recorded by hand.")
    public WorkerDtos.Response markSettled(@PathVariable Long workerId,
                                           @AuthenticationPrincipal AuthenticatedUser caller,
                                           HttpServletRequest httpRequest) {
        return workerService.markSettled(workerId, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{workerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Delete a worker",
            description = "Soft delete, and refused once they have any deployment on record - "
                    + "that would orphan the attendance and billing history. Record their exit "
                    + "instead.")
    public void delete(@PathVariable Long workerId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        workerService.delete(workerId, caller, RequestContext.from(httpRequest));
    }

    // ==================================================================
    // Wage structures
    // ==================================================================

    @GetMapping("/{workerId}/salary-structures")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_READ + "')")
    @Operation(summary = "A worker's wage history, newest first",
            description = "Dated history: each revision closed the one before it. Render it as "
                    + "a timeline, not an editable grid.")
    public List<SalaryStructureDtos.Response> salaryHistory(@PathVariable Long workerId) {
        return salaryStructureService.historyForWorker(workerId);
    }

    @GetMapping("/{workerId}/salary-structures/effective")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_READ + "')")
    @Operation(summary = "The wage structure in force on a date",
            description = "What Phase 8 calls when recomputing an earlier month - the answer "
                    + "has to be the structure that applied then, not the current one.")
    public SalaryStructureDtos.Response salaryEffectiveOn(
            @PathVariable Long workerId,
            @Parameter(description = "Defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
        return salaryStructureService.effectiveOn(workerId, onDate);
    }

    @PostMapping("/{workerId}/salary-structures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
    @Operation(summary = "Set a wage structure, superseding the current one",
            description = """
                    The only way to change a wage. The current structure is closed the day
                    before `effectiveFrom`; nothing is edited in place, because payslips have
                    been issued against it.

                    Components are separate columns because the deductions have different
                    bases: PF is on basic + DA, ESI is on gross below a ceiling, HRA has its
                    own exemption. One gross figure would make correct deductions impossible
                    later.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "422",
                    description = "Starts on or before the current structure, before the "
                            + "joining date, or the worker has left (BUSINESS_RULE_VIOLATION)")
    })
    public SalaryStructureDtos.Response setSalaryStructure(
            @PathVariable Long workerId,
            @Valid @RequestBody SalaryStructureDtos.Request request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return salaryStructureService.supersede(
                workerId, request, caller, RequestContext.from(httpRequest));
    }
}
