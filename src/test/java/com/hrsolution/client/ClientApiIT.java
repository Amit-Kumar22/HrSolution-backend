package com.hrsolution.client;

import com.hrsolution.notification.service.EmailService;
import com.hrsolution.support.AbstractIntegrationTest;
import com.hrsolution.user.entity.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clients, sites, contracts and rate cards end to end.
 *
 * <p>The test that matters most is
 * {@link #clientUserCannotReachAnotherClient()}. Client ids are sequential
 * integers, so without an ownership check any client user could read a
 * competitor's rates, sites and contracts by changing a number in the URL. The
 * permission alone does not prevent that - {@code CLIENT_READ} is exactly what
 * a client user is supposed to hold.
 */
class ClientApiIT extends AbstractIntegrationTest {

    private static final String CLIENTS = "/api/v1/clients";

    @MockitoBean
    private EmailService emailService;

    // ==================================================================
    // Clients
    // ==================================================================

    @Test
    @DisplayName("creates a client, derives its code and resolves GST treatment")
    void createClient() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);

        // The company's own state code is 27 (seeded by V1), so a client in 27
        // is intra-state and one in 29 is inter-state.
        String body = mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Bharat Textiles Private Limited %s",
                                 "tradeName":"Bharat Textiles",
                                 "billingCity":"Pune","billingState":"Maharashtra",
                                 "billingStateCode":"27","paymentTermsDays":45}
                                """.formatted(unique())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientCode").value(containsString("CLI-")))
                .andExpect(jsonPath("$.displayName").value("Bharat Textiles"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.paymentTermsDays").value(45))
                .andExpect(jsonPath("$.gstTreatment").value("INTRA_STATE"))
                .andExpect(jsonPath("$.onboardedOn").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long clientId = longField(body, "id");

        // A client in another state flips to IGST.
        mockMvc.perform(put(CLIENTS + "/" + clientId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Bharat Textiles Private Limited %s",
                                 "billingState":"Karnataka","billingStateCode":"29"}
                                """.formatted(suffixOf(body))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gstTreatment").value("INTER_STATE"));
    }

    @Test
    @DisplayName("a client with no state code reports UNKNOWN rather than guessing")
    void missingStateCodeIsUnknown() throws Exception {
        // Onboarding before the paperwork arrives is normal; guessing a tax
        // treatment is not. Phase 9 refuses to invoice on UNKNOWN.
        mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"No Paperwork Yet Pvt Ltd %s"}
                                """.formatted(unique())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gstTreatment").value("UNKNOWN"));
    }

    @Test
    @DisplayName("rejects a duplicate GSTIN")
    void duplicateGstinRejected() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        String gstin = "27AABCB1234C1Z5";

        mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"First Co %s","gstin":"%s","billingStateCode":"27"}
                                """.formatted(unique(), gstin)))
                .andExpect(status().isCreated());

        // A GSTIN is unique nationally, so a duplicate means the same company
        // was entered twice.
        mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Second Co %s","gstin":"%s","billingStateCode":"27"}
                                """.formatted(unique(), gstin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("validates GSTIN, PAN and state code formats")
    void validatesIdentifiers() throws Exception {
        mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"","gstin":"NOPE","pan":"123",
                                 "billingStateCode":"999","billingPincode":"0123",
                                 "paymentTermsDays":400}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("legalName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("gstin")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("pan")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("billingStateCode")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("paymentTermsDays")));
    }

    // ==================================================================
    // Cross-client isolation - the important one
    // ==================================================================

    @Test
    @DisplayName("a client user cannot reach another client's data by changing the id")
    void clientUserCannotReachAnotherClient() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);

        long myClientId = createClientReturningId(adminToken, "My Company");
        long rivalClientId = createClientReturningId(adminToken, "Rival Company");

        // A CLIENT-role login linked to my company only.
        String clientEmail = createUser(RoleName.CLIENT);
        long clientUserId = userIdOf(adminToken, clientEmail);

        mockMvc.perform(post(CLIENTS + "/" + myClientId + "/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"designation":"Plant Head","primaryContact":true}
                                """.formatted(clientUserId)))
                .andExpect(status().isCreated());

        String clientToken = "Bearer " + loginForAccessToken(clientEmail, TEST_PASSWORD);

        // Own client: fine.
        mockMvc.perform(get(CLIENTS + "/" + myClientId)
                        .header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(myClientId));

        // Rival client: 404, NOT 403. A 403 would confirm the record exists and
        // turn sequential ids into a way to enumerate the customer list.
        mockMvc.perform(get(CLIENTS + "/" + rivalClientId)
                        .header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Every nested resource is guarded the same way.
        mockMvc.perform(get(CLIENTS + "/" + rivalClientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(CLIENTS + "/" + rivalClientId + "/contracts")
                        .header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(CLIENTS + "/" + rivalClientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isNotFound());

        // And a forged list filter cannot widen the result set - it is
        // overridden with the caller's own client id rather than honoured.
        mockMvc.perform(get(CLIENTS + "?clientId=" + rivalClientId)
                        .header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(myClientId));

        // Staff see both.
        mockMvc.perform(get(CLIENTS + "?search=Company")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) myClientId)))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) rivalClientId)));

        // /clients/me takes no id, so it cannot be pointed anywhere else.
        mockMvc.perform(get(CLIENTS + "/me").header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(myClientId));
    }

    @Test
    @DisplayName("a login belongs to exactly one client")
    void oneLoginOneClient() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long firstClient = createClientReturningId(adminToken, "First Client");
        long secondClient = createClientReturningId(adminToken, "Second Client");

        String clientEmail = createUser(RoleName.CLIENT);
        long userId = userIdOf(adminToken, clientEmail);

        mockMvc.perform(post(CLIENTS + "/" + firstClient + "/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(userId)))
                .andExpect(status().isCreated());

        // Otherwise one account could read two companies' data, and every
        // ownership check would have to cope with a set of client ids.
        mockMvc.perform(post(CLIENTS + "/" + secondClient + "/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("only a CLIENT-role user can be linked to a client")
    void onlyClientRoleCanBeLinked() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Role Check Co");

        String workerEmail = createUser(RoleName.WORKER);
        long workerUserId = userIdOf(adminToken, workerEmail);

        mockMvc.perform(post(CLIENTS + "/" + clientId + "/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(workerUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    // ==================================================================
    // Sites
    // ==================================================================

    @Test
    @DisplayName("adds sites, keeps codes unique per client, and allows a different state")
    void sites() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Multi Site Co");

        // A Pune-billed client running a plant in Gujarat. The site's state is
        // what governs minimum wage and PT for workers there.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"SURAT-PLANT-1","siteName":"Surat Dyeing Unit",
                                 "city":"Surat","state":"Gujarat","stateCode":"24",
                                 "siteInchargeName":"Mahesh Patel","siteInchargePhone":"9876543210"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteCode").value("SURAT-PLANT-1"))
                .andExpect(jsonPath("$.stateCode").value("24"))
                .andExpect(jsonPath("$.displayLabel")
                        .value("SURAT-PLANT-1 — Surat Dyeing Unit"))
                .andExpect(jsonPath("$.active").value(true));

        // Same code twice for one client is a conflict.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"SURAT-PLANT-1","siteName":"Duplicate"}
                                """))
                .andExpect(status().isConflict());

        // Lower-case input is accepted and normalised. The service upper-cases
        // the code, so a pattern that rejected lower case would make that
        // normalisation unreachable - and reject a perfectly clear request.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"pune-ho","siteName":"Pune Head Office",
                                 "stateCode":"27"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteCode").value("PUNE-HO"));

        // And the normalised form collides with the existing one, rather than
        // creating a near-duplicate that differs only by case.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"Pune-HO","siteName":"Case Duplicate"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("a suspended client cannot have sites added")
    void suspendedClientRejectsNewSites() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Suspended Co");

        mockMvc.perform(patch(CLIENTS + "/" + clientId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED","reason":"Non-payment"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NEW-SITE\",\"siteName\":\"New\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    // ==================================================================
    // Contracts
    // ==================================================================

    @Test
    @DisplayName("draft, activate, and refuse an overlapping second active contract")
    void contractLifecycleAndOverlap() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Contract Co");

        long firstId = createContract(adminToken, clientId,
                "CT/%s/01".formatted(unique()), "2026-04-01", "2027-03-31", "PERCENTAGE", "8.50");

        // Created DRAFT - activating is a separate, deliberate act, because
        // that is what makes the terms binding and freezes them.
        mockMvc.perform(get("/api/v1/contracts/" + firstId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.serviceChargeLabel").value("8.5% of wages"))
                .andExpect(jsonPath("$.inForceToday").value(false));

        mockMvc.perform(patch("/api/v1/contracts/" + firstId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // An overlapping second contract can be drafted...
        long overlappingId = createContract(adminToken, clientId,
                "CT/%s/02".formatted(unique()), "2026-10-01", "2027-09-30",
                "FIXED_PER_WORKER", "1200");

        // ...but not activated: two active contracts covering the same day would
        // make the service charge on an invoice ambiguous.
        mockMvc.perform(patch("/api/v1/contracts/" + overlappingId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detail").value(containsString("ambiguous")));

        // An active contract's terms are frozen - invoices may depend on them.
        mockMvc.perform(put("/api/v1/contracts/" + firstId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractNumber":"CT/CHANGED/01","startDate":"2026-04-01",
                                 "serviceChargeType":"PERCENTAGE","serviceChargeValue":"15.00"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("no longer be edited")));
    }

    @Test
    @DisplayName("finds the contract in force on a date, and 404s when none is")
    void findContractInForce() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Billing Lookup Co");

        long contractId = createContract(adminToken, clientId,
                "CT/%s/IF".formatted(unique()), "2026-04-01", "2027-03-31", "PERCENTAGE", "10");
        mockMvc.perform(patch("/api/v1/contracts/" + contractId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(CLIENTS + "/" + clientId + "/contracts/in-force?onDate=2026-06-15")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contractId));

        // Billing a period with no contract must fail loudly, not silently
        // apply someone else's terms.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/contracts/in-force?onDate=2025-06-15")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("terminating sets the end date so later billing finds no contract")
    void terminationClosesTheBillingWindow() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Terminate Co");

        long contractId = createContract(adminToken, clientId,
                "CT/%s/T".formatted(unique()), "2026-04-01", null, "PERCENTAGE", "8");
        mockMvc.perform(patch("/api/v1/contracts/" + contractId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/contracts/" + contractId + "/terminate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"terminatedOn":"2026-09-30","reason":"Client closed the plant"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                // The termination date becomes the effective end date.
                .andExpect(jsonPath("$.endDate").value("2026-09-30"));

        mockMvc.perform(get(CLIENTS + "/" + clientId + "/contracts/in-force?onDate=2026-10-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rejects a percentage service charge above 100")
    void rejectsImpossiblePercentage() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Bad Percent Co");

        // 850 is nonsense as a percentage but an ordinary per-worker fee, which
        // is why the bound depends on the type and cannot be an annotation.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/contracts")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractNumber":"CT/%s/BAD","startDate":"2026-04-01",
                                 "serviceChargeType":"PERCENTAGE","serviceChargeValue":"850"}
                                """.formatted(unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("serviceChargeValue"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(containsString("FIXED_PER_WORKER")));

        // The same number is accepted as a per-worker fee.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/contracts")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractNumber":"CT/%s/OK","startDate":"2026-04-01",
                                 "serviceChargeType":"FIXED_PER_WORKER","serviceChargeValue":"850"}
                                """.formatted(unique())))
                .andExpect(status().isCreated());
    }

    // ==================================================================
    // Rate cards - supersession and resolution
    // ==================================================================

    @Test
    @DisplayName("a new rate supersedes the old one instead of overwriting it")
    void rateCardSupersession() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Rate History Co");
        long categoryId = firstCategoryId(adminToken);

        // April's rate.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"18000.00",
                                 "billingRate":"22500.00","effectiveFrom":"2026-04-01"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.grossMarginPerWorker").value(4500.00))
                .andExpect(jsonPath("$.billingBelowWage").value(false));

        // October's revision.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"19500.00",
                                 "billingRate":"24000.00","effectiveFrom":"2026-10-01"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(true));

        // Both rows survive. The old one is closed the day before the new one
        // starts, so every past date still resolves to the rate that applied.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.effectiveFrom=='2026-04-01')].effectiveTo")
                        .value(hasItem("2026-09-30")));

        // June resolves to April's rate - this is what makes re-running an
        // earlier payroll reproduce the original figures.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/rate-cards/resolve"
                        + "?categoryId=" + categoryId + "&onDate=2026-06-15")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCard.monthlyWage").value(18000.00))
                .andExpect(jsonPath("$.ambiguous").value(false))
                .andExpect(jsonPath("$.resolution").value(containsString("Client-wide")));

        // November resolves to October's.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/rate-cards/resolve"
                        + "?categoryId=" + categoryId + "&onDate=2026-11-15")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCard.monthlyWage").value(19500.00));

        // Before any rate existed: no rate card, and an explanation.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/rate-cards/resolve"
                        + "?categoryId=" + categoryId + "&onDate=2025-01-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCard").doesNotExist())
                .andExpect(jsonPath("$.resolution").value(containsString("No rate card covers")));
    }

    @Test
    @DisplayName("a site-specific rate beats the client-wide one")
    void siteSpecificRateWins() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Two State Co");
        long categoryId = firstCategoryId(adminToken);

        String siteBody = mockMvc.perform(post(CLIENTS + "/" + clientId + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"GUJ-1","siteName":"Gujarat Unit","stateCode":"24"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long siteId = longField(siteBody, "id");

        // Client-wide rate.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"18000.00",
                                 "billingRate":"22500.00","effectiveFrom":"2026-04-01"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated());

        // Gujarat override - different state, different minimum wage.
        mockMvc.perform(post(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"siteId":%d,"monthlyWage":"16500.00",
                                 "billingRate":"20500.00","effectiveFrom":"2026-04-01"}
                                """.formatted(categoryId, siteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteSpecific").value(true));

        // With the site: the override wins, and that is not "ambiguous" - the
        // client-wide row is a fallback, not a competing rate.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/rate-cards/resolve"
                        + "?categoryId=" + categoryId + "&siteId=" + siteId + "&onDate=2026-06-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCard.monthlyWage").value(16500.00))
                .andExpect(jsonPath("$.resolution").value(containsString("Site-specific")))
                .andExpect(jsonPath("$.ambiguous").value(false));

        // Without the site: the client-wide rate.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/rate-cards/resolve"
                        + "?categoryId=" + categoryId + "&onDate=2026-06-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCard.monthlyWage").value(18000.00))
                .andExpect(jsonPath("$.resolution").value(containsString("Client-wide")));
    }

    @Test
    @DisplayName("a rate that has taken effect cannot be edited or deleted")
    void effectiveRatesAreImmutable() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Immutable Rate Co");
        long categoryId = firstCategoryId(adminToken);

        String pastRate = mockMvc.perform(post(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"18000.00",
                                 "billingRate":"22500.00","effectiveFrom":"%s"}
                                """.formatted(categoryId, LocalDate.now().minusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pastRateId = longField(pastRate, "id");

        // It may already have produced a wage or an invoice, so editing it
        // would rewrite history.
        mockMvc.perform(put("/api/v1/rate-cards/" + pastRateId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"1.00","billingRate":"2.00",
                                 "effectiveFrom":"2026-04-01"}
                                """.formatted(categoryId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("already have been used")));

        mockMvc.perform(delete("/api/v1/rate-cards/" + pastRateId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isUnprocessableContent());

        // A future-dated rate is still correctable.
        String futureRate = mockMvc.perform(post(CLIENTS + "/" + clientId + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"19000.00",
                                 "billingRate":"23000.00","effectiveFrom":"%s"}
                                """.formatted(categoryId, LocalDate.now().plusMonths(2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(put("/api/v1/rate-cards/" + longField(futureRate, "id"))
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"monthlyWage":"19500.00",
                                 "billingRate":"23500.00","effectiveFrom":"%s"}
                                """.formatted(categoryId, LocalDate.now().plusMonths(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyWage").value(19500.00));
    }

    @Test
    @DisplayName("a rate card cannot borrow another client's site")
    void rateCardCannotUseForeignSite() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientA = createClientReturningId(adminToken, "Client A Rates");
        long clientB = createClientReturningId(adminToken, "Client B Rates");
        long categoryId = firstCategoryId(adminToken);

        String siteBody = mockMvc.perform(post(CLIENTS + "/" + clientB + "/sites")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"B-SITE\",\"siteName\":\"B Site\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(CLIENTS + "/" + clientA + "/rate-cards")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"siteId":%d,"monthlyWage":"18000.00",
                                 "billingRate":"22500.00","effectiveFrom":"2026-04-01"}
                                """.formatted(categoryId, longField(siteBody, "id"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("siteId"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(containsString("different client")));
    }

    // ==================================================================
    // Registration approval - the Phase 2 hand-off completed
    // ==================================================================

    @Test
    @DisplayName("approving a registration creates the client and links the login")
    void approveRegistrationCreatesClientAndLink() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        String registrantEmail = "reg-" + unique() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Shree Fabrics","firstName":"Rajesh",
                                 "lastName":"Kulkarni","email":"%s","phone":"9876500000",
                                 "password":"%s","consentGiven":true}
                                """.formatted(registrantEmail, TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        long userId = userIdOf(adminToken, registrantEmail);

        // Sign-in is blocked before approval.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(registrantEmail, TEST_PASSWORD)))
                .andExpect(status().isForbidden());

        // One call creates the client, links the login as primary contact, and
        // activates the account. The legal name is corrected from the
        // registrant's self-declared version, which is kept as the trade name.
        String approved = mockMvc.perform(
                        post(CLIENTS + "/approve-registration/" + userId)
                                .header(HttpHeaders.AUTHORIZATION, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"legalName":"Shree Fabrics Private Limited",
                                         "gstin":"27AACCS9876F1Z2","billingCity":"Pune",
                                         "billingState":"Maharashtra","billingStateCode":"27",
                                         "designation":"Purchase Manager","paymentTermsDays":30}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalName").value("Shree Fabrics Private Limited"))
                .andExpect(jsonPath("$.tradeName").value("Shree Fabrics"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.gstTreatment").value("INTRA_STATE"))
                .andReturn().getResponse().getContentAsString();

        long clientId = longField(approved, "id");

        // The login is now ACTIVE. Creating the client and the link without
        // activating the account would leave the whole approval pointless -
        // every sign-in would still return 403.
        mockMvc.perform(get("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerified").value(true));

        // The login now works and resolves to the new client with no id in the URL.
        String clientToken = "Bearer " + loginForAccessToken(registrantEmail, TEST_PASSWORD);
        mockMvc.perform(get(CLIENTS + "/me").header(HttpHeaders.AUTHORIZATION, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId));

        // Linked as primary contact, since they are the obvious first point of
        // contact for the company they registered.
        mockMvc.perform(get(CLIENTS + "/" + clientId + "/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(registrantEmail))
                .andExpect(jsonPath("$[0].primaryContact").value(true))
                .andExpect(jsonPath("$[0].designation").value("Purchase Manager"));

        // Approving twice is refused.
        mockMvc.perform(post(CLIENTS + "/approve-registration/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Shree Fabrics Private Limited\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("already been approved")));
    }

    @Test
    @DisplayName("the old Phase 2 approve endpoint refuses to create a half-state account")
    void legacyApproveRefusesWithoutClientLink() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        String registrantEmail = "legacy-" + unique() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Legacy Path Co","firstName":"Test",
                                 "email":"%s","phone":"9876500001","password":"%s",
                                 "consentGiven":true}
                                """.formatted(registrantEmail, TEST_PASSWORD)))
                .andExpect(status().isCreated());

        long userId = userIdOf(adminToken, registrantEmail);

        // Activating the login alone would let them sign in and see nothing,
        // because every client-scoped screen resolves through client_users.
        mockMvc.perform(patch("/api/v1/users/" + userId + "/approve-client")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("approve-registration")));
    }

    // ==================================================================
    // Authorisation
    // ==================================================================

    @Test
    @DisplayName("writing requires CLIENT_WRITE; approving requires CLIENT_APPROVE")
    void authorisation() throws Exception {
        String hrToken = bearerFor(RoleName.HR_RECRUITER);
        String accountsToken = bearerFor(RoleName.ACCOUNTS);
        String workerToken = bearerFor(RoleName.WORKER);

        // HR and ACCOUNTS hold CLIENT_READ but not CLIENT_WRITE.
        mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, hrToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, accountsToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Should Fail\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // A WORKER holds no client permission at all.
        mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isForbidden());

        // CLIENT_APPROVE sits with SUPER_ADMIN and ADMIN only.
        mockMvc.perform(post(CLIENTS + "/approve-registration/1")
                        .header(HttpHeaders.AUTHORIZATION, accountsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(CLIENTS)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a client cannot be deleted while an active contract exists")
    void deleteBlockedByActiveContract() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);
        long clientId = createClientReturningId(adminToken, "Has Contract Co");

        long contractId = createContract(adminToken, clientId,
                "CT/%s/DEL".formatted(unique()), "2026-04-01", "2027-03-31", "PERCENTAGE", "8");
        mockMvc.perform(patch("/api/v1/contracts/" + contractId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(CLIENTS + "/" + clientId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("active contract")));

        mockMvc.perform(patch("/api/v1/contracts/" + contractId + "/terminate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"terminatedOn":"2026-06-30","reason":"Test"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete(CLIENTS + "/" + clientId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
    }

    // ==================================================================
    // Skills
    // ==================================================================

    @Test
    @DisplayName("skills are seeded, readable by any user, and editable with CONTENT_MANAGE")
    void skills() throws Exception {
        mockMvc.perform(get("/api/v1/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.WORKER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("PSARA Trained")));

        // The seeded category-skill mapping from V4.
        String adminToken = bearerFor(RoleName.ADMIN);
        long categoryId = categoryIdByCode(adminToken, "SECURITY_GUARD");

        mockMvc.perform(get("/api/v1/manpower-categories/" + categoryId + "/skills")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("PSARA Trained")));

        // Unknown ids are reported rather than silently dropped.
        mockMvc.perform(put("/api/v1/manpower-categories/" + categoryId + "/skills")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillIds\":[999999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skillIds"));

        mockMvc.perform(post("/api/v1/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.WORKER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Fail\"}"))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String unique() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String suffixOf(String clientJson) {
        String name = stringField(clientJson, "legalName");
        return name.substring(name.lastIndexOf(' ') + 1);
    }

    private long createClientReturningId(String adminToken, String namePrefix) throws Exception {
        String body = mockMvc.perform(post(CLIENTS)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"%s %s","billingCity":"Pune",
                                 "billingState":"Maharashtra","billingStateCode":"27"}
                                """.formatted(namePrefix, unique())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    private long createContract(String adminToken, long clientId, String number,
                                String start, String end, String chargeType,
                                String chargeValue) throws Exception {
        String endJson = end == null ? "" : ",\"endDate\":\"%s\"".formatted(end);
        String body = mockMvc.perform(post(CLIENTS + "/" + clientId + "/contracts")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractNumber":"%s","startDate":"%s"%s,
                                 "serviceChargeType":"%s","serviceChargeValue":"%s"}
                                """.formatted(number, start, endJson, chargeType, chargeValue)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    private long firstCategoryId(String adminToken) throws Exception {
        return categoryIdByCode(adminToken, "SECURITY_GUARD");
    }

    private long categoryIdByCode(String adminToken, String code) throws Exception {
        String body = mockMvc.perform(get("/api/v1/manpower-categories")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int codeIndex = body.indexOf("\"code\":\"" + code + "\"");
        assertThat(codeIndex).as("category %s should be seeded", code).isGreaterThan(-1);
        // The id precedes the code in each object, so search backwards.
        String upToCode = body.substring(0, codeIndex);
        int idMarker = upToCode.lastIndexOf("\"id\":");
        return Long.parseLong(upToCode.substring(idMarker + 5, upToCode.indexOf(',', idMarker)));
    }

    private long userIdOf(String adminToken, String email) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users?search=" + email)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    private String stringField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private long longField(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker) + marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
