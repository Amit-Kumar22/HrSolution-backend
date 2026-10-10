package com.hrsolution.client.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.client.dto.ClientDtos;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientStatus;
import com.hrsolution.client.entity.ClientUser;
import com.hrsolution.client.entity.GstTreatment;
import com.hrsolution.client.mapper.ClientMapper;
import com.hrsolution.client.repository.ClientContractRepository;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.client.repository.ClientUserRepository;
import com.hrsolution.client.entity.ContractStatus;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.content.entity.Industry;
import com.hrsolution.content.repository.IndustryRepository;
import com.hrsolution.settings.service.CompanySettingsService;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Client companies, their portal users, and the status lifecycle.
 *
 * <p>Every method taking a client id calls {@link ClientAccessGuard} first.
 * Client ids are sequential integers, so without that check a client user could
 * read a competitor's record by changing a number in the URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientUserRepository clientUserRepository;
    private final ClientSiteRepository clientSiteRepository;
    private final ClientContractRepository contractRepository;
    private final IndustryRepository industryRepository;
    private final UserRepository userRepository;
    private final ClientMapper clientMapper;
    private final ClientAccessGuard accessGuard;
    private final CompanySettingsService companySettingsService;
    private final AuditService auditService;

    // ==================================================================
    // Queries
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<ClientDtos.SummaryResponse> list(String search,
                                                         ClientStatus status,
                                                         String stateCode,
                                                         Long requestedClientId,
                                                         AuthenticatedUser caller,
                                                         Pageable pageable) {
        // For a client user this returns their own id whatever the request
        // asked for, so a forged filter cannot widen the result set.
        Long scopedClientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);

        Specification<Client> specification = Specification.<Client>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("id", scopedClientId))
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("billingStateCode", stateCode))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "legalName", "tradeName", "gstin", "billingCity"));

        String companyStateCode = companyStateCode();
        Page<Client> page = clientRepository.findAll(specification, pageable);

        return PageResponse.from(page, client -> withGstTreatment(
                clientMapper.toSummaryResponse(client), client, companyStateCode));
    }

    @Transactional(readOnly = true)
    public ClientDtos.Response get(Long clientId, AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        return enrich(load(clientId));
    }

    /** Client plus users, sites and contracts, for the overview screen. */
    @Transactional(readOnly = true)
    public ClientDtos.DetailResponse getDetail(Long clientId, AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        Client client = load(clientId);

        return new ClientDtos.DetailResponse(
                enrich(client),
                clientUserRepository.findByClientId(clientId).stream()
                        .map(clientMapper::toUserResponse).toList(),
                clientSiteRepository.findByClientId(clientId).stream()
                        .map(clientMapper::toResponse).toList(),
                contractRepository.findByClientId(clientId).stream()
                        .map(clientMapper::toResponse).toList());
    }

    /** The calling client user's own company, with no id in the URL at all. */
    @Transactional(readOnly = true)
    public ClientDtos.Response getOwnClient(AuthenticatedUser caller) {
        Long clientId = accessGuard.ownClientId(caller)
                .orElseThrow(() -> new BusinessRuleException(
                        "Your account is not linked to a client company."));
        return enrich(load(clientId));
    }

    // ==================================================================
    // Create and update
    // ==================================================================

    @Transactional
    public ClientDtos.Response create(ClientDtos.Request request,
                                      AuthenticatedUser actor,
                                      RequestContext context) {
        rejectDuplicates(request.gstin(), request.legalName(), null);

        Client client = new Client();
        clientMapper.applyRequest(request, client);
        client.setGstin(normaliseUpper(request.gstin()));
        client.setPan(normaliseUpper(request.pan()));
        client.setCin(normaliseUpper(request.cin()));
        client.setIndustry(resolveIndustry(request.industryId()));
        client.setPaymentTermsDays(request.paymentTermsDays() == null ? 30 : request.paymentTermsDays());
        // Created directly by staff, so it is trading immediately - unlike a
        // self-registration, which arrives as PENDING_APPROVAL.
        client.setStatus(ClientStatus.ACTIVE);
        client.setOnboardedOn(LocalDate.now());

        Client saved = clientRepository.save(client);
        warnIfGstTreatmentUnknown(saved);

        auditService.recordEntityChange(AuditAction.CREATE, "Client", saved.getId(),
                actor.id(), actor.email(),
                "Created client '%s' (%s)".formatted(saved.getLegalName(), saved.clientCode()),
                null, saved.getLegalName(), context);

        log.info("Created client {} '{}' state={} gst={}",
                saved.clientCode(), saved.getLegalName(), saved.getBillingStateCode(),
                saved.gstTreatmentAgainst(companyStateCode()));

        return enrich(saved);
    }

    @Transactional
    public ClientDtos.Response update(Long clientId,
                                      ClientDtos.Request request,
                                      AuthenticatedUser actor,
                                      RequestContext context) {
        accessGuard.requireAccessTo(clientId, actor);
        Client client = load(clientId);

        rejectDuplicates(request.gstin(), request.legalName(), clientId);

        String previousStateCode = client.getBillingStateCode();

        clientMapper.applyRequest(request, client);
        client.setGstin(normaliseUpper(request.gstin()));
        client.setPan(normaliseUpper(request.pan()));
        client.setCin(normaliseUpper(request.cin()));
        client.setIndustry(resolveIndustry(request.industryId()));
        if (request.paymentTermsDays() != null) {
            client.setPaymentTermsDays(request.paymentTermsDays());
        }

        // Changing the state code flips GST treatment for every invoice issued
        // from now on, so it is called out rather than logged as a field diff.
        if (!java.util.Objects.equals(previousStateCode, client.getBillingStateCode())) {
            log.warn("Client {} billing state code changed {} -> {}. GST treatment is now {}. "
                            + "Invoices already issued are unaffected.",
                    client.clientCode(), previousStateCode, client.getBillingStateCode(),
                    client.gstTreatmentAgainst(companyStateCode()));
        }
        warnIfGstTreatmentUnknown(client);

        auditService.recordEntityChange(AuditAction.UPDATE, "Client", client.getId(),
                actor.id(), actor.email(),
                "Updated client " + client.clientCode(),
                previousStateCode, client.getBillingStateCode(), context);

        return enrich(client);
    }

    // ==================================================================
    // Status
    // ==================================================================

    @Transactional
    public ClientDtos.Response changeStatus(Long clientId,
                                            ClientDtos.StatusChangeRequest request,
                                            AuthenticatedUser actor,
                                            RequestContext context) {
        Client client = load(clientId);
        ClientStatus previous = client.getStatus();

        if (previous == request.status()) {
            throw new BusinessRuleException(
                    "This client is already " + previous + ".");
        }
        // Approval creates the client record and must go through
        // approveRegistration, which also links the login and sets up the
        // commercial details. Flipping the status alone would leave a client
        // nobody can sign in to.
        if (request.status() == ClientStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(
                    "A client cannot be moved back to PENDING_APPROVAL.");
        }

        client.setStatus(request.status());
        if (request.status() == ClientStatus.ACTIVE && client.getOnboardedOn() == null) {
            client.setOnboardedOn(LocalDate.now());
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Client", clientId,
                actor.id(), actor.email(),
                "Client %s status %s -> %s%s".formatted(client.clientCode(), previous,
                        request.status(),
                        request.reason() == null ? "" : ": " + request.reason()),
                previous.name(), request.status().name(), context);

        log.info("Client {} status {} -> {} by {}",
                client.clientCode(), previous, request.status(), actor.email());

        return enrich(client);
    }

    /** Soft delete. Invoices and payroll reference clients for years. */
    @Transactional
    public void delete(Long clientId, AuthenticatedUser actor, RequestContext context) {
        Client client = load(clientId);

        long activeContracts = contractRepository
                .countByClientIdAndStatusAndDeletedFalse(clientId, ContractStatus.ACTIVE);
        if (activeContracts > 0) {
            throw new BusinessRuleException(
                    ("This client has %d active contract(s). Terminate or expire them first - "
                            + "deleting a client with a live contract would orphan the billing "
                            + "terms its invoices depend on.").formatted(activeContracts));
        }

        client.markDeleted(actor.email());
        client.setStatus(ClientStatus.INACTIVE);

        auditService.recordEntityChange(AuditAction.DELETE, "Client", clientId,
                actor.id(), actor.email(),
                "Deleted client " + client.clientCode(), null, null, context);
    }

    // ==================================================================
    // Portal users
    // ==================================================================

    @Transactional(readOnly = true)
    public List<ClientDtos.UserResponse> listUsers(Long clientId, AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        return clientUserRepository.findByClientId(clientId).stream()
                .map(clientMapper::toUserResponse)
                .toList();
    }

    @Transactional
    public ClientDtos.UserResponse linkUser(Long clientId,
                                            ClientDtos.LinkUserRequest request,
                                            AuthenticatedUser actor,
                                            RequestContext context) {
        Client client = load(clientId);

        User user = userRepository.findActiveByIdWithRoles(request.userId())
                .orElseThrow(() -> FieldValidationException.of("userId",
                        "is not a known active user"));

        if (!user.hasRole(RoleName.CLIENT)) {
            throw FieldValidationException.of("userId",
                    "must hold the CLIENT role before being linked to a client company");
        }
        // One login, one client. Enforced by a unique index too, but checked
        // here so the caller gets a clear message rather than a constraint
        // violation.
        if (clientUserRepository.existsByUserId(user.getId())) {
            throw new DuplicateResourceException(
                    "That user is already linked to a client company. A login belongs to exactly "
                            + "one client.");
        }

        ClientUser link = new ClientUser();
        link.setClient(client);
        link.setUser(user);
        link.setDesignation(trimToNull(request.designation()));

        if (Boolean.TRUE.equals(request.primaryContact())) {
            clientUserRepository.clearPrimaryContactFor(clientId);
            link.setPrimaryContact(true);
        }

        ClientUser saved = clientUserRepository.save(link);

        auditService.recordEntityChange(AuditAction.UPDATE, "Client", clientId,
                actor.id(), actor.email(),
                "Linked user %s to client %s".formatted(user.getEmail(), client.clientCode()),
                null, user.getEmail(), context);

        return clientMapper.toUserResponse(saved);
    }

    @Transactional
    public ClientDtos.UserResponse setPrimaryContact(Long clientId,
                                                     Long clientUserId,
                                                     AuthenticatedUser actor,
                                                     RequestContext context) {
        accessGuard.requireAccessTo(clientId, actor);
        ClientUser link = loadLink(clientUserId, clientId);

        // Clear first, then set: "at most one primary contact" cannot be a
        // MySQL unique constraint, so the invariant lives here.
        clientUserRepository.clearPrimaryContactFor(clientId);
        link.setPrimaryContact(true);

        auditService.recordEntityChange(AuditAction.UPDATE, "Client", clientId,
                actor.id(), actor.email(),
                "Primary contact for %s is now %s"
                        .formatted(link.getClient().clientCode(), link.getUser().getEmail()),
                null, link.getUser().getEmail(), context);

        return clientMapper.toUserResponse(link);
    }

    @Transactional
    public ClientDtos.UserResponse setUserActive(Long clientId,
                                                 Long clientUserId,
                                                 boolean active,
                                                 AuthenticatedUser actor,
                                                 RequestContext context) {
        ClientUser link = loadLink(clientUserId, clientId);

        if (!active && link.isPrimaryContact()) {
            throw new BusinessRuleException(
                    "This person is the primary contact. Nominate someone else first, so "
                            + "contract and invoice notifications still reach a human.");
        }

        link.setActive(active);

        auditService.recordEntityChange(AuditAction.UPDATE, "Client", clientId,
                actor.id(), actor.email(),
                "%s access for %s".formatted(active ? "Restored" : "Revoked",
                        link.getUser().getEmail()),
                null, String.valueOf(active), context);

        return clientMapper.toUserResponse(link);
    }

    // ==================================================================
    // Registration approval - the Phase 2 hand-off completed
    // ==================================================================

    /**
     * Approves a self-registered client: creates the {@code clients} row, links
     * the login, and activates the account.
     *
     * <p>Phase 2 could only activate the login, because there was no client
     * table to create a record in. This is the other half, and it is one
     * transaction on purpose: a user activated without a client link can sign in
     * but see nothing, and a client created without an activated user is a
     * record nobody can reach.
     */
    @Transactional
    public ClientDtos.Response approveRegistration(Long userId,
                                                   ClientDtos.ApproveRegistrationRequest request,
                                                   AuthenticatedUser actor,
                                                   RequestContext context) {
        User user = userRepository.findActiveByIdWithRoles(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (!user.hasRole(RoleName.CLIENT)) {
            throw new BusinessRuleException("That account is not a client registration.");
        }
        if (clientUserRepository.existsByUserId(userId)) {
            throw new BusinessRuleException(
                    "That registration has already been approved and linked to a client.");
        }
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(
                    ("Only a PENDING_APPROVAL registration can be approved; this account is %s.")
                            .formatted(user.getStatus()));
        }

        rejectDuplicates(request.gstin(), request.legalName(), null);

        Client client = new Client();
        client.setLegalName(request.legalName().trim());
        // The registrant's self-declared name is kept as the trade name when it
        // differs from the corrected legal name - it is what they call
        // themselves, and staff will recognise it.
        if (user.getPendingCompanyName() != null
                && !user.getPendingCompanyName().equalsIgnoreCase(request.legalName().trim())) {
            client.setTradeName(user.getPendingCompanyName());
        }
        client.setGstin(normaliseUpper(request.gstin()));
        client.setPan(normaliseUpper(request.pan()));
        client.setBillingCity(trimToNull(request.billingCity()));
        client.setBillingState(trimToNull(request.billingState()));
        client.setBillingStateCode(trimToNull(request.billingStateCode()));
        client.setPaymentTermsDays(request.paymentTermsDays() == null ? 30 : request.paymentTermsDays());
        client.setStatus(ClientStatus.ACTIVE);
        client.setOnboardedOn(LocalDate.now());

        Client savedClient = clientRepository.save(client);

        ClientUser link = new ClientUser();
        link.setClient(savedClient);
        link.setUser(user);
        link.setDesignation(trimToNull(request.designation()));
        // The person who registered is the obvious first point of contact.
        link.setPrimaryContact(true);
        clientUserRepository.save(link);

        // Activate the login. Without this the whole approval is pointless: the
        // client record and the link exist, but the account stays
        // PENDING_APPROVAL and every sign-in attempt returns 403. An admin
        // approving the registration also vouches for the email address, so it
        // is marked verified rather than sending a separate link.
        user.setStatus(UserStatus.ACTIVE);
        user.markEmailVerified();

        warnIfGstTreatmentUnknown(savedClient);

        auditService.recordEntityChange(AuditAction.CLIENT_APPROVED, "Client", savedClient.getId(),
                actor.id(), actor.email(),
                "Approved registration of %s (%s); created client %s and linked %s"
                        .formatted(request.legalName(), user.getEmail(),
                                savedClient.clientCode(), user.getEmail()),
                user.getPendingCompanyName(), savedClient.getLegalName(), context);

        log.info("Approved client registration for {}: created {} '{}'",
                user.getEmail(), savedClient.clientCode(), savedClient.getLegalName());

        return enrich(savedClient);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private Client load(Long clientId) {
        return clientRepository.findActiveById(clientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));
    }

    private ClientUser loadLink(Long clientUserId, Long clientId) {
        ClientUser link = clientUserRepository.findByIdWithUserAndClient(clientUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Client user", clientUserId));

        // The link must belong to the client in the path, or a caller could
        // mutate another client's contacts by pairing ids from two clients.
        if (!link.getClient().getId().equals(clientId)) {
            throw ResourceNotFoundException.of("Client user", clientUserId);
        }
        return link;
    }

    /** Fills in the fields the mapper deliberately leaves out. */
    private ClientDtos.Response enrich(Client client) {
        ClientDtos.Response base = clientMapper.toResponse(client);
        int sites = (int) clientSiteRepository
                .countByClientIdAndActiveTrueAndDeletedFalse(client.getId());
        int contracts = (int) contractRepository
                .countByClientIdAndStatusAndDeletedFalse(client.getId(), ContractStatus.ACTIVE);

        return new ClientDtos.Response(
                base.id(), base.clientCode(), base.legalName(), base.tradeName(),
                base.displayName(), base.gstin(), base.pan(), base.cin(),
                base.billingAddressLine1(), base.billingAddressLine2(), base.billingCity(),
                base.billingState(), base.billingStateCode(), base.billingPincode(),
                base.billingCountry(), base.industryId(), base.industryName(),
                base.status(), base.paymentTermsDays(), base.onboardedOn(), base.notes(),
                client.gstTreatmentAgainst(companyStateCode()),
                sites, contracts);
    }

    private ClientDtos.SummaryResponse withGstTreatment(ClientDtos.SummaryResponse base,
                                                        Client client,
                                                        String companyStateCode) {
        return new ClientDtos.SummaryResponse(
                base.id(), base.clientCode(), base.legalName(), base.displayName(),
                base.gstin(), base.billingCity(), base.billingState(), base.status(),
                client.gstTreatmentAgainst(companyStateCode), base.onboardedOn());
    }

    /** The company's own GST state code, read from the cached company profile. */
    private String companyStateCode() {
        return companySettingsService.get().stateCode();
    }

    /**
     * Logs loudly when GST treatment cannot be determined.
     *
     * <p>Not an error at this point - a client is often onboarded before its
     * paperwork arrives. But Phase 9 will refuse to issue an invoice, so the
     * sooner somebody notices the missing state code the better.
     */
    private void warnIfGstTreatmentUnknown(Client client) {
        if (client.gstTreatmentAgainst(companyStateCode()) == GstTreatment.UNKNOWN) {
            log.warn("Client {} has no usable GST state code (client='{}', company='{}'). "
                            + "Invoicing will be blocked until both are set.",
                    client.clientCode(), client.getBillingStateCode(), companyStateCode());
        }
    }

    private void rejectDuplicates(String gstin, String legalName, Long excludeClientId) {
        String normalisedGstin = normaliseUpper(gstin);

        if (normalisedGstin != null && clientRepository.existsByGstinIgnoringSoftDelete(normalisedGstin)) {
            boolean sameClient = excludeClientId != null
                    && clientRepository.findActiveById(excludeClientId)
                    .map(existing -> normalisedGstin.equals(existing.getGstin()))
                    .orElse(false);
            if (!sameClient) {
                throw DuplicateResourceException.of("A client", "GSTIN", normalisedGstin);
            }
        }

        if (excludeClientId == null && clientRepository.existsByLegalNameIgnoreCase(legalName.trim())) {
            throw DuplicateResourceException.of("A client", "legal name", legalName.trim());
        }
    }

    private Industry resolveIndustry(Long industryId) {
        if (industryId == null) {
            return null;
        }
        return industryRepository.findById(industryId)
                .orElseThrow(() -> FieldValidationException.of("industryId",
                        "is not a known industry"));
    }

    private String normaliseUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
