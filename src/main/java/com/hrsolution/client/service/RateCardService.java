package com.hrsolution.client.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.client.dto.RateCardDtos;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.entity.RateCard;
import com.hrsolution.client.mapper.ClientMapper;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.client.repository.RateCardRepository;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Rate cards: the agreed wage and billing rate per client, per category,
 * optionally per site.
 *
 * <h2>Rates are history, not settings</h2>
 *
 * <p>{@link #create} does not overwrite the current rate. It inserts a successor
 * and closes the predecessor the day before, so every past date still resolves
 * to the rate that applied then. This matters because:
 *
 * <ul>
 *   <li>re-running March's payroll must produce March's figures again;</li>
 *   <li>crediting an old invoice must use the rate that was billed;</li>
 *   <li>a wage dispute is settled by what the rate card said at the time.</li>
 * </ul>
 *
 * <p>Editing in place would silently rewrite all three.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateCardService {

    private final RateCardRepository rateCardRepository;
    private final ClientRepository clientRepository;
    private final ClientSiteRepository siteRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final ClientMapper clientMapper;
    private final ClientAccessGuard accessGuard;
    private final AuditService auditService;

    // ==================================================================
    // Queries
    // ==================================================================

    @Transactional(readOnly = true)
    public List<RateCardDtos.Response> listForClient(Long clientId, AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        return rateCardRepository.findByClientId(clientId).stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RateCardDtos.Response get(Long rateCardId, AuthenticatedUser caller) {
        RateCard rateCard = load(rateCardId);
        accessGuard.requireAccessTo(rateCard.getClient().getId(), caller);
        return clientMapper.toResponse(rateCard);
    }

    /**
     * Resolves the rate applicable to a category at a site on a date.
     *
     * <p>This is the lookup payroll (Phase 8) and billing (Phase 9) will call,
     * so it reports <em>how</em> it chose as well as what it chose. Specificity
     * first - a site-specific rate beats a client-wide one - then recency.
     *
     * <p>An ambiguous result is surfaced rather than hidden. Two rows claiming
     * the same date means the rate cards overlap, and silently taking the first
     * would change someone's wage based on row order.
     */
    @Transactional(readOnly = true)
    public RateCardDtos.ResolvedResponse resolve(Long clientId,
                                                 Long categoryId,
                                                 Long siteId,
                                                 LocalDate onDate,
                                                 AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        LocalDate effectiveDate = onDate == null ? LocalDate.now() : onDate;

        List<RateCard> candidates = rateCardRepository
                .findApplicableOn(clientId, categoryId, siteId, effectiveDate);

        if (candidates.isEmpty()) {
            return new RateCardDtos.ResolvedResponse(null,
                    ("No rate card covers %s for category %d%s. Create one effective from that "
                            + "date or earlier before deploying or billing.")
                            .formatted(effectiveDate, categoryId,
                                    siteId == null ? "" : " at site " + siteId),
                    false);
        }

        RateCard chosen = candidates.getFirst();

        // Ambiguity means two rows of the SAME specificity both cover the date.
        // A site-specific row plus a client-wide fallback is normal and not
        // ambiguous - that is the override working as intended.
        long sameSpecificity = candidates.stream()
                .filter(candidate -> candidate.isSiteSpecific() == chosen.isSiteSpecific())
                .count();
        boolean ambiguous = sameSpecificity > 1;

        if (ambiguous) {
            log.warn("Ambiguous rate cards for client {} category {} site {} on {}: {} rows of "
                            + "equal specificity overlap. Using id {}.",
                    clientId, categoryId, siteId, effectiveDate, sameSpecificity, chosen.getId());
        }

        String resolution = "%s rate effective from %s%s".formatted(
                chosen.isSiteSpecific() ? "Site-specific" : "Client-wide",
                chosen.getEffectiveFrom(),
                ambiguous ? " (AMBIGUOUS: " + sameSpecificity + " overlapping rows - fix these)" : "");

        return new RateCardDtos.ResolvedResponse(
                clientMapper.toResponse(chosen), resolution, ambiguous);
    }

    // ==================================================================
    // Create - supersedes rather than overwrites
    // ==================================================================

    @Transactional
    public RateCardDtos.Response create(Long clientId,
                                        RateCardDtos.Request request,
                                        AuthenticatedUser actor,
                                        RequestContext context) {
        Client client = clientRepository.findActiveById(clientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));

        ManpowerCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> FieldValidationException.of("categoryId",
                        "is not a known manpower category"));

        ClientSite site = resolveSite(request.siteId(), clientId);

        // Close any open row for this exact combination, rather than ending up
        // with two rows both claiming to be current.
        List<RateCard> openRows = rateCardRepository.findOpenRows(
                clientId, request.categoryId(), request.siteId());

        for (RateCard open : openRows) {
            if (!open.getEffectiveFrom().isBefore(request.effectiveFrom())) {
                throw new BusinessRuleException(
                        ("The current rate for this category starts on %s, which is on or after "
                                + "the %s you asked for. A new rate must start after the one it "
                                + "supersedes.")
                                .formatted(open.getEffectiveFrom(), request.effectiveFrom()));
            }
            open.closeBefore(request.effectiveFrom());
            log.info("Closed rate card {} at {} ahead of its successor",
                    open.getId(), open.getEffectiveTo());
        }

        // Catch any remaining overlap - a historical row whose explicit
        // effectiveTo still spans the new date.
        List<RateCard> overlapping = rateCardRepository.findOverlapping(
                clientId, request.categoryId(), request.siteId(),
                request.effectiveFrom(), null, null);
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException(
                    ("%d existing rate card(s) for this category and site already cover %s. "
                            + "Overlapping rates make the applicable wage depend on row order, "
                            + "so fix the existing dates first.")
                            .formatted(overlapping.size(), request.effectiveFrom()));
        }

        RateCard rateCard = new RateCard();
        rateCard.setClient(client);
        rateCard.setCategory(category);
        rateCard.setSite(site);
        rateCard.setMonthlyWage(request.monthlyWage());
        rateCard.setBillingRate(request.billingRate());
        rateCard.setOtRatePerHour(request.otRatePerHour());
        rateCard.setShiftHours(request.shiftHours() == null ? 8 : request.shiftHours());
        rateCard.setEffectiveFrom(request.effectiveFrom());
        rateCard.setNotes(request.notes());

        RateCard saved = rateCardRepository.save(rateCard);

        // A warning, not a rejection: a loss-leader on one category of a large
        // account is a real commercial decision, and refusing it would be the
        // system overruling the person who negotiated the deal.
        if (saved.isBillingBelowWage()) {
            log.warn("Rate card {} for client {} category {} bills {} against a wage of {} - "
                            + "a margin of {}. Confirm this is intentional.",
                    saved.getId(), client.clientCode(), category.getName(),
                    saved.getBillingRate(), saved.getMonthlyWage(),
                    saved.grossMarginPerWorker());
        }

        auditService.recordEntityChange(AuditAction.CREATE, "RateCard", saved.getId(),
                actor.id(), actor.email(),
                "Rate card for %s / %s%s effective %s: wage %s, billing %s".formatted(
                        client.clientCode(), category.getName(),
                        site == null ? " (all sites)" : " @ " + site.getSiteCode(),
                        saved.getEffectiveFrom(), saved.getMonthlyWage(), saved.getBillingRate()),
                null, saved.getBillingRate().toPlainString(), context);

        return clientMapper.toResponse(saved);
    }

    /**
     * Corrects a rate card's figures in place.
     *
     * <p>Only permitted while the rate has not yet taken effect. Once
     * {@code effectiveFrom} has passed, the rate may already have been used to
     * compute a wage or an invoice, and changing it would alter history -
     * supersede it with a new row instead.
     */
    @Transactional
    public RateCardDtos.Response correct(Long rateCardId,
                                         RateCardDtos.Request request,
                                         AuthenticatedUser actor,
                                         RequestContext context) {
        RateCard rateCard = load(rateCardId);

        if (!rateCard.getEffectiveFrom().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    ("This rate took effect on %s and may already have been used for payroll or "
                            + "billing. Create a new rate card effective from a future date "
                            + "instead of editing this one.")
                            .formatted(rateCard.getEffectiveFrom()));
        }

        java.math.BigDecimal previousBilling = rateCard.getBillingRate();

        rateCard.setMonthlyWage(request.monthlyWage());
        rateCard.setBillingRate(request.billingRate());
        rateCard.setOtRatePerHour(request.otRatePerHour());
        if (request.shiftHours() != null) {
            rateCard.setShiftHours(request.shiftHours());
        }
        rateCard.setNotes(request.notes());

        auditService.recordEntityChange(AuditAction.UPDATE, "RateCard", rateCardId,
                actor.id(), actor.email(),
                "Corrected not-yet-effective rate card " + rateCardId,
                previousBilling.toPlainString(), rateCard.getBillingRate().toPlainString(),
                context);

        return clientMapper.toResponse(rateCard);
    }

    /**
     * Soft-deletes a rate card.
     *
     * <p>Refused once it has taken effect, for the same reason corrections are:
     * payroll and invoices may already depend on it.
     */
    @Transactional
    public void delete(Long rateCardId, AuthenticatedUser actor, RequestContext context) {
        RateCard rateCard = load(rateCardId);

        if (!rateCard.getEffectiveFrom().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    ("This rate took effect on %s and may already have been used. Supersede it "
                            + "with a new rate card rather than deleting it.")
                            .formatted(rateCard.getEffectiveFrom()));
        }

        rateCard.markDeleted(actor.email());

        auditService.recordEntityChange(AuditAction.DELETE, "RateCard", rateCardId,
                actor.id(), actor.email(),
                "Deleted not-yet-effective rate card " + rateCardId, null, null, context);
    }

    // ==================================================================

    private RateCard load(Long rateCardId) {
        return rateCardRepository.findActiveById(rateCardId)
                .orElseThrow(() -> ResourceNotFoundException.of("Rate card", rateCardId));
    }

    /** Resolves a site id, insisting it belongs to the same client. */
    private ClientSite resolveSite(Long siteId, Long clientId) {
        if (siteId == null) {
            return null;
        }
        ClientSite site = siteRepository.findActiveById(siteId)
                .orElseThrow(() -> FieldValidationException.of("siteId", "is not a known site"));

        // Without this, a rate card could attach one client's site to another
        // client's pricing.
        if (!site.getClient().getId().equals(clientId)) {
            throw FieldValidationException.of("siteId",
                    "belongs to a different client");
        }
        return site;
    }
}
