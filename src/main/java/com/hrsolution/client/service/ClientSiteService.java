package com.hrsolution.client.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.client.dto.ClientSiteDtos;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.mapper.ClientMapper;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Client sites - the locations where workers are actually deployed.
 *
 * <p>The state code on a site is treated carefully throughout. It is not
 * cosmetic address data: it selects the minimum wage, the professional tax slab
 * and the contract labour licence that apply to everyone working there, so a
 * change to it is logged explicitly rather than folded into a generic field
 * diff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSiteService {

    private final ClientSiteRepository siteRepository;
    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;
    private final ClientAccessGuard accessGuard;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ClientSiteDtos.Response> listForClient(Long clientId, AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        return siteRepository.findByClientId(clientId).stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientSiteDtos.Response get(Long siteId, AuthenticatedUser caller) {
        ClientSite site = load(siteId);
        accessGuard.requireAccessTo(site.getClient().getId(), caller);
        return clientMapper.toResponse(site);
    }

    @Transactional
    public ClientSiteDtos.Response create(Long clientId,
                                          ClientSiteDtos.Request request,
                                          AuthenticatedUser actor,
                                          RequestContext context) {
        Client client = clientRepository.findActiveById(clientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));

        if (!client.allowsNewWork()) {
            throw new BusinessRuleException(
                    ("This client is %s, so new sites cannot be added. Reactivate the client "
                            + "first.").formatted(client.getStatus()));
        }

        String siteCode = request.siteCode().trim().toUpperCase();
        if (siteRepository.existsByClientAndSiteCode(clientId, siteCode)) {
            throw DuplicateResourceException.of("A site of this client", "code", siteCode);
        }

        ClientSite site = new ClientSite();
        clientMapper.applyRequest(request, site);
        site.setClient(client);
        site.setSiteCode(siteCode);
        site.setActive(request.active() == null || request.active());

        ClientSite saved = siteRepository.save(site);

        if (saved.getStateCode() == null || saved.getStateCode().isBlank()) {
            // Not fatal yet, but Phase 8 cannot compute a legal wage without it.
            log.warn("Site {} of client {} has no state code. Minimum wage and professional tax "
                            + "cannot be determined for workers deployed here.",
                    saved.getSiteCode(), client.clientCode());
        }

        auditService.recordEntityChange(AuditAction.CREATE, "ClientSite", saved.getId(),
                actor.id(), actor.email(),
                "Added site %s (%s) to client %s".formatted(
                        saved.getSiteCode(), saved.getSiteName(), client.clientCode()),
                null, saved.getSiteCode(), context);

        return clientMapper.toResponse(saved);
    }

    @Transactional
    public ClientSiteDtos.Response update(Long siteId,
                                          ClientSiteDtos.Request request,
                                          AuthenticatedUser actor,
                                          RequestContext context) {
        ClientSite site = load(siteId);
        accessGuard.requireAccessTo(site.getClient().getId(), actor);

        String newCode = request.siteCode().trim().toUpperCase();
        if (!site.getSiteCode().equals(newCode)
                && siteRepository.existsByClientAndSiteCode(site.getClient().getId(), newCode)) {
            throw DuplicateResourceException.of("A site of this client", "code", newCode);
        }

        String previousStateCode = site.getStateCode();

        clientMapper.applyRequest(request, site);
        site.setSiteCode(newCode);
        if (request.active() != null) {
            site.setActive(request.active());
        }

        // Called out separately because it changes the legal wage floor and the
        // PT slab for everyone deployed at this site from now on.
        if (!java.util.Objects.equals(previousStateCode, site.getStateCode())) {
            log.warn("Site {} state code changed {} -> {}. Minimum wage and professional tax for "
                            + "workers here change accordingly; past payroll is unaffected.",
                    site.getSiteCode(), previousStateCode, site.getStateCode());
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "ClientSite", siteId,
                actor.id(), actor.email(),
                "Updated site " + site.getSiteCode(),
                previousStateCode, site.getStateCode(), context);

        return clientMapper.toResponse(site);
    }

    /**
     * Deactivates a site rather than deleting it.
     *
     * <p>Deployments, attendance sheets and invoices all reference sites, so a
     * site that has ever been used must remain resolvable. Deactivating removes
     * it from new deployment dropdowns and leaves history intact.
     */
    @Transactional
    public ClientSiteDtos.Response setActive(Long siteId,
                                             boolean active,
                                             AuthenticatedUser actor,
                                             RequestContext context) {
        ClientSite site = load(siteId);
        accessGuard.requireAccessTo(site.getClient().getId(), actor);

        site.setActive(active);

        auditService.recordEntityChange(AuditAction.UPDATE, "ClientSite", siteId,
                actor.id(), actor.email(),
                "%s site %s".formatted(active ? "Activated" : "Deactivated", site.getSiteCode()),
                null, String.valueOf(active), context);

        return clientMapper.toResponse(site);
    }

    @Transactional
    public void delete(Long siteId, AuthenticatedUser actor, RequestContext context) {
        ClientSite site = load(siteId);
        site.markDeleted(actor.email());
        site.setActive(false);

        auditService.recordEntityChange(AuditAction.DELETE, "ClientSite", siteId,
                actor.id(), actor.email(),
                "Deleted site " + site.getSiteCode(), null, null, context);
    }

    private ClientSite load(Long siteId) {
        return siteRepository.findActiveById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
    }
}
