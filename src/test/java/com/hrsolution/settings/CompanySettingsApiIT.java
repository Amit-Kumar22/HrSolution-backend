package com.hrsolution.settings;

import com.hrsolution.support.AbstractIntegrationTest;
import com.hrsolution.user.entity.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the Phase 1 foundation.
 *
 * <p>Simply reaching the first assertion already proves a good deal: the context
 * started, Flyway applied {@code V1} to a real MySQL 8.4, and
 * {@code ddl-auto=validate} agreed that {@code CompanySettings} matches the
 * {@code company_settings} table it created.
 *
 * <p>Beyond that, these tests pin down the error contract that every later phase
 * inherits, so a regression in {@code GlobalExceptionHandler} is caught here
 * rather than by a client.
 */
class CompanySettingsApiIT extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/settings/company";

    /**
     * Phase 2 closed these endpoints. Reading the company profile now needs any
     * valid token; editing it needs SETTINGS_MANAGE, which only SUPER_ADMIN
     * holds. The unauthenticated cases are asserted explicitly in
     * {@link #requiresAuthentication()} and {@link #updateRequiresSettingsManage()}.
     */
    private String superAdminToken;
    private String workerToken;

    @BeforeEach
    void signIn() throws Exception {
        superAdminToken = bearerFor(RoleName.SUPER_ADMIN);
        workerToken = bearerFor(RoleName.WORKER);
    }

    @Test
    @DisplayName("reads the Flyway-seeded profile, updates it, and reads back the new values")
    void readUpdateReadFlow() throws Exception {
        // 1. The seed row from migration V1 is there.
        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.legalName").value("Your Company Private Limited"))
                .andExpect(jsonPath("$.stateCode").value("27"))
                .andExpect(jsonPath("$.city").value("Pune"))
                // Written by the Flyway seed, which runs with no principal and
                // so lands on AuditorAwareImpl's fallback.
                .andExpect(jsonPath("$.updatedBy").value("flyway"));

        // 2. A valid update is accepted and echoed back.
        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "Shree Manpower Services Private Limited",
                                  "tradeName": "Shree Manpower",
                                  "city": "Nagpur",
                                  "state": "Maharashtra",
                                  "stateCode": "27",
                                  "pincode": "440001",
                                  "country": "India",
                                  "phone": "+91 712 2345678",
                                  "email": "info@shreemanpower.example.com",
                                  "gstin": "27AABCS1234A1Z5",
                                  "pan": "AABCS1234A",
                                  "bankName": "HDFC Bank",
                                  "bankAccountNumber": "50100123456789",
                                  "bankIfsc": "HDFC0001234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Shree Manpower Services Private Limited"))
                .andExpect(jsonPath("$.gstin").value("27AABCS1234A1Z5"))
                // The audit columns must reflect THIS write, not the previous
                // one. They are only populated at flush, so a plain save() would
                // return the seeded 'flyway' value while the database held the
                // new one. Now that the request is authenticated, AuditorAwareImpl
                // resolves the caller's email rather than the 'system' fallback.
                .andExpect(jsonPath("$.updatedBy").value(startsWith("it-super_admin-")));

        // 3. A fresh read reflects the update - which also proves the @CacheEvict
        //    on update actually cleared the @Cacheable read.
        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Shree Manpower Services Private Limited"))
                .andExpect(jsonPath("$.city").value("Nagpur"))
                .andExpect(jsonPath("$.bankIfsc").value("HDFC0001234"))
                // Full-replacement semantics: tagline was not sent, so it is now
                // cleared, and non_null inclusion omits it from the response.
                .andExpect(jsonPath("$.tagline").doesNotExist());
    }

    @Test
    @DisplayName("rejects an invalid body with a field-level ProblemDetail")
    void rejectsInvalidBody() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "",
                                  "gstin": "NOT-A-GSTIN",
                                  "pan": "123",
                                  "pincode": "0123",
                                  "bankIfsc": "hdfc1234",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // The RFC 7807 envelope, plus this project's extensions.
                .andExpect(jsonPath("$.type").value(startsWith("https://")))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value(ENDPOINT))
                .andExpect(jsonPath("$.path").value(ENDPOINT))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists())
                // Every bad field is reported, not just the first one.
                .andExpect(jsonPath("$.errors", hasSize(6)))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("legalName", "gstin", "pan", "pincode", "bankIfsc", "email")));
    }

    @Test
    @DisplayName("never echoes a rejected bank account number back to the caller")
    void masksSensitiveRejectedValues() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "Valid Name",
                                  "bankAccountNumber": "not-a-valid-account-number"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bankAccountNumber"))
                // The field is named in the error, but its value is withheld so
                // it cannot be replayed out of a log or an error-tracking tool.
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
                .andExpect(content().string(not(containsStringIgnoringCase("not-a-valid-account-number"))));
    }

    @Test
    @DisplayName("returns MALFORMED_REQUEST for unparseable JSON without leaking parser internals")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON."))
                // Jackson's own message names classes and byte offsets.
                .andExpect(content().string(not(containsStringIgnoringCase("jackson"))));
    }

    @Test
    @DisplayName("returns 404 with a JSON body for an unknown endpoint")
    void unknownEndpointReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No endpoint exists at this path."));
    }

    @Test
    @DisplayName("returns 405 for a method the endpoint does not support")
    void unsupportedMethod() throws Exception {
        mockMvc.perform(delete(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("echoes a supplied correlation id, and generates one when absent")
    void correlationId() throws Exception {
        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .header("X-Correlation-Id", "manual-trace-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "manual-trace-001"));

        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("strips characters from an inbound correlation id that could forge a log line")
    void sanitisesCorrelationId() throws Exception {
        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .header("X-Correlation-Id", "abc\tdef ghi"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abcdefghi"));
    }

    @Test
    @DisplayName("sets the baseline security headers on every response")
    void securityHeaders() throws Exception {
        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy",
                        startsWith("default-src 'self'")));
    }

    @Test
    @DisplayName("exposes the health probe without authentication")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("serves the OpenAPI document with the bearer scheme already declared")
    void openApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("HR Solution API"))
                .andExpect(jsonPath("$.paths['/api/v1/settings/company']").exists())
                // Declared in Phase 1 so Phase 2 only has to reference it.
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    // ------------------------------------------------------------------
    // Authorisation, closed by Phase 2
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reading the company profile requires a token")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("any signed-in user may read it - the portal header needs it")
    void anySignedInUserMayRead() throws Exception {
        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").isNotEmpty());
    }

    @Test
    @DisplayName("editing it requires SETTINGS_MANAGE, which a WORKER does not hold")
    void updateRequiresSettingsManage() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Hijacked Pvt Ltd\"}"))
                // 403, not 401: the caller is authenticated, just not permitted.
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("an unknown path under the API is a 401 before it is a 404")
    void unknownPathRequiresAuthFirst() throws Exception {
        // Deliberate: an anonymous caller must not be able to probe which
        // endpoints exist. Authenticated, the same path gives a proper 404 -
        // asserted in unknownEndpointReturnsProblemDetail above.
        mockMvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    /** Hamcrest's case-insensitive substring matcher, imported here for brevity. */
    private static org.hamcrest.Matcher<String> containsStringIgnoringCase(String substring) {
        return org.hamcrest.Matchers.containsStringIgnoringCase(substring);
    }
}
