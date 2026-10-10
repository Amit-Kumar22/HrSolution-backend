package com.hrsolution.client.controller;

import com.hrsolution.client.dto.RateCardDtos;
import com.hrsolution.client.service.RateCardService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Rate cards: the agreed wage and billing rate per client, per category,
 * optionally per site.
 *
 * <p><strong>Rate cards are dated history, not current settings.</strong>
 * Creating one supersedes the previous rate rather than replacing it - the old
 * row is closed off the day before - so every past date still resolves to the
 * rate that applied then. That is what lets March's payroll be re-run and an
 * old invoice be credited correctly.
 *
 * <p>A client user can see their own rates, including the wage. That is
 * deliberate: under the Contract Labour (R&amp;A) Act the principal employer is
 * liable for ensuring contract workers receive at least the minimum wage, so a
 * client that cannot see the wage cannot discharge its own obligation.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Rate cards", description = "Agreed wages and billing rates per client and category")
public class RateCardController {

    private final RateCardService rateCardService;

    @GetMapping("/clients/{clientId}/rate-cards")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "List a client's rate cards",
            description = "Includes superseded history, so the full rate timeline is visible. "
                    + "The `current` flag marks the open row for each combination.")
    public List<RateCardDtos.Response> list(@PathVariable Long clientId,
                                            @AuthenticationPrincipal AuthenticatedUser caller) {
        return rateCardService.listForClient(clientId, caller);
    }

    @GetMapping("/clients/{clientId}/rate-cards/resolve")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Resolve the rate applicable on a date",
            description = """
                    The lookup payroll (Phase 8) and billing (Phase 9) will call. Selection is \
                    by specificity, then recency:

                    1. a rate naming the site beats a client-wide rate for the same category — \
                    minimum wages differ by state, so a client with plants in two states needs \
                    per-site rates to be correct;
                    2. among equally specific rows, the latest effectiveFrom on or before the \
                    date wins.

                    The response reports HOW it chose, and sets `ambiguous` when two rows of \
                    equal specificity both cover the date — that means the rate cards overlap \
                    and need fixing, because otherwise the applicable wage would depend on row \
                    order.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The applicable rate, or a null rateCard with an explanation"),
            @ApiResponse(responseCode = "404", description = "No such client, or not yours")
    })
    public RateCardDtos.ResolvedResponse resolve(
            @PathVariable Long clientId,
            @Parameter(required = true) @RequestParam Long categoryId,
            @Parameter(description = "Omit for the client-wide rate")
            @RequestParam(required = false) Long siteId,
            @Parameter(description = "Defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return rateCardService.resolve(clientId, categoryId, siteId, onDate, caller);
    }

    @GetMapping("/rate-cards/{rateCardId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Get one rate card")
    public RateCardDtos.Response get(@PathVariable Long rateCardId,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return rateCardService.get(rateCardId, caller);
    }

    @PostMapping("/clients/{clientId}/rate-cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Create a rate card, superseding the current one",
            description = """
                    Does NOT overwrite. Any open rate for the same client, category and site is \
                    closed off the day before `effectiveFrom`, and this becomes the new current \
                    row — so recomputing an earlier month still uses the earlier rate.

                    Billing below the wage is allowed but logged as a warning: a loss-leader on \
                    one category of a large account is a real commercial decision, and refusing \
                    it would be the system overruling whoever negotiated the deal.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; any previous rate closed off"),
            @ApiResponse(responseCode = "400",
                    description = "Unknown category, or a site belonging to another client (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "422",
                    description = "effectiveFrom is not after the rate it supersedes, or dates "
                            + "overlap existing rows (BUSINESS_RULE_VIOLATION)")
    })
    public RateCardDtos.Response create(@PathVariable Long clientId,
                                        @Valid @RequestBody RateCardDtos.Request request,
                                        @AuthenticationPrincipal AuthenticatedUser caller,
                                        HttpServletRequest httpRequest) {
        return rateCardService.create(clientId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/rate-cards/{rateCardId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Correct a rate card that has not taken effect yet",
            description = "Permitted only while effectiveFrom is in the future. Once the date "
                    + "has passed the rate may already have produced a wage or an invoice, and "
                    + "editing it would rewrite history - supersede it instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Corrected"),
            @ApiResponse(responseCode = "422",
                    description = "The rate has already taken effect (BUSINESS_RULE_VIOLATION)")
    })
    public RateCardDtos.Response correct(@PathVariable Long rateCardId,
                                         @Valid @RequestBody RateCardDtos.Request request,
                                         @AuthenticationPrincipal AuthenticatedUser caller,
                                         HttpServletRequest httpRequest) {
        return rateCardService.correct(rateCardId, request, caller,
                RequestContext.from(httpRequest));
    }

    @DeleteMapping("/rate-cards/{rateCardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Delete a rate card that has not taken effect yet",
            description = "Same rule as correcting: refused once the rate may have been used.")
    public void delete(@PathVariable Long rateCardId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        rateCardService.delete(rateCardId, caller, RequestContext.from(httpRequest));
    }
}
