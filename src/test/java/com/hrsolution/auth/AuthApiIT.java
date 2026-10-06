package com.hrsolution.auth;

import com.hrsolution.notification.service.EmailService;
import com.hrsolution.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authentication against real MySQL: register, verify, sign in,
 * rotate, detect reuse, and the authorisation boundaries.
 *
 * <p>{@link EmailService} is mocked because the verification and reset tokens
 * only ever exist in an email - the database holds their SHA-256 hash, so there
 * is no way to recover a usable token from it. Capturing the argument is
 * therefore the only route to the token, and that is by design.
 */
class AuthApiIT extends AbstractIntegrationTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String REFRESH_COOKIE = "hrs_refresh";
    private static final String PASSWORD = "Str0ng@Pass1";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private com.hrsolution.common.ratelimit.RateLimitService rateLimitService;

    /** Unique per test, so tests sharing the container cannot collide on email. */
    private String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    // ==================================================================
    // Registration and verification
    // ==================================================================

    private record Registered(String email, String verificationToken) {
    }

    private Registered registerAndCaptureToken() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post(AUTH + "/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Asha",
                                  "lastName": "Patil",
                                  "email": "%s",
                                  "phone": "9876543210",
                                  "password": "%s",
                                  "consentGiven": true
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(
                org.mockito.ArgumentMatchers.eq(email), anyString(), tokenCaptor.capture());

        return new Registered(email, tokenCaptor.getValue());
    }

    @Test
    @DisplayName("register, verify, then sign in")
    void registerVerifyLogin() throws Exception {
        Registered registered = registerAndCaptureToken();

        // Sign-in is blocked before the address is confirmed. 403 and not 401:
        // the password was correct, the account simply is not usable yet.
        mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(registered.email(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("confirm your email")));

        mockMvc.perform(post(AUTH + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(registered.verificationToken())))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(registered.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(registered.email()))
                .andExpect(jsonPath("$.user.roles[0]").value("CANDIDATE"))
                .andReturn();

        Cookie refreshCookie = login.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refreshCookie).isNotNull();
        // The three attributes that matter, asserted rather than assumed.
        assertThat(refreshCookie.isHttpOnly()).as("HttpOnly keeps it away from script").isTrue();
        assertThat(refreshCookie.getPath())
                .as("narrow path keeps it off ordinary API calls").isEqualTo("/api/v1/auth");
        assertThat(refreshCookie.getAttribute("SameSite"))
                .as("SameSite=Strict is the CSRF defence").isEqualTo("Strict");

        // The refresh token must never appear in the body, where script could read it.
        assertThat(login.getResponse().getContentAsString())
                .doesNotContain(refreshCookie.getValue());
    }

    @Test
    @DisplayName("a verification token cannot be used twice")
    void verificationTokenIsSingleUse() throws Exception {
        Registered registered = registerAndCaptureToken();
        String body = "{\"token\":\"%s\"}".formatted(registered.verificationToken());

        mockMvc.perform(post(AUTH + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("registering an email twice is a 409, not a 500 from the unique index")
    void duplicateEmailIsConflict() throws Exception {
        Registered registered = registerAndCaptureToken();

        mockMvc.perform(post(AUTH + "/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Other","email":"%s","phone":"9876543211",
                                 "password":"%s","consentGiven":true}
                                """.formatted(registered.email(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("registration rejects a weak password and a missing consent together")
    void registrationValidation() throws Exception {
        mockMvc.perform(post(AUTH + "/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Asha","email":"%s","phone":"12345",
                                 "password":"weak","consentGiven":false}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        org.hamcrest.Matchers.hasItems("password", "phone", "consentGiven")))
                // The rejected password must never be echoed back.
                .andExpect(jsonPath("$.errors[?(@.field=='password')].rejectedValue")
                        .value(org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("a client registration lands PENDING_APPROVAL and notifies an admin")
    void clientRegistrationAwaitsApproval() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post(AUTH + "/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Bharat Textiles Private Limited",
                                 "firstName":"Rajesh","lastName":"Kulkarni","email":"%s",
                                 "phone":"9876500000","password":"%s","consentGiven":true}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        verify(emailService).sendAdminClientRegistrationNotification(
                org.mockito.ArgumentMatchers.eq("Bharat Textiles Private Limited"),
                anyString(), org.mockito.ArgumentMatchers.eq(email), anyString());

        // Correct password, but a human has to approve a client login before it
        // can see deployed workers and invoices.
        mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("awaiting approval")));
    }

    // ==================================================================
    // Token rotation and reuse detection
    // ==================================================================

    @Test
    @DisplayName("refresh rotates the cookie, and replaying the old one kills the family")
    void refreshRotationAndReuseDetection() throws Exception {
        Cookie firstCookie = verifiedUserCookie();

        // 1. A normal refresh issues a different cookie.
        MvcResult firstRefresh = mockMvc.perform(post(AUTH + "/refresh").cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie secondCookie = firstRefresh.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(secondCookie).isNotNull();
        assertThat(secondCookie.getValue())
                .as("every refresh must issue a new token")
                .isNotEqualTo(firstCookie.getValue());

        // 2. Replaying the consumed token is treated as theft.
        mockMvc.perform(post(AUTH + "/refresh").cookie(firstCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        // 3. The crucial assertion: the successor the legitimate client is
        //    holding has ALSO been revoked. Revoking only the replayed token
        //    would leave an attacker who had already rotated it still signed in.
        mockMvc.perform(post(AUTH + "/refresh").cookie(secondCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh without a cookie is a 401, not a 500")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post(AUTH + "/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("logout revokes the token and clears the cookie")
    void logoutRevokes() throws Exception {
        Cookie cookie = verifiedUserCookie();

        MvcResult logout = mockMvc.perform(post(AUTH + "/logout").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).as("max-age 0 expires the cookie").isZero();

        mockMvc.perform(post(AUTH + "/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout succeeds even with no cookie at all")
    void logoutIsIdempotent() throws Exception {
        // The caller's goal - end my session - is already met, so an error here
        // would only leave a client stuck.
        mockMvc.perform(post(AUTH + "/logout"))
                .andExpect(status().isOk());
    }

    // ==================================================================
    // Lockout and enumeration resistance
    // ==================================================================

    @Test
    @DisplayName("five wrong passwords lock the account for fifteen minutes")
    void accountLocksAfterFiveFailures() throws Exception {
        String email = verifiedUserEmail();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post(AUTH + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "Wrong@Password9")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        }

        // The sixth attempt reports the lock - even with the CORRECT password,
        // which is what makes the lockout a real brute-force defence.
        mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("locked")));
    }

    @Test
    @DisplayName("an unknown email and a wrong password are indistinguishable")
    void loginDoesNotRevealWhetherAnAccountExists() throws Exception {
        String realEmail = verifiedUserEmail();

        String unknownResponse = mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueEmail(), "Wrong@Password9")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPasswordResponse = mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(realEmail, "Wrong@Password9")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Identical status and identical detail. Any difference would let an
        // attacker test a list of addresses for valid accounts.
        assertThat(detailOf(unknownResponse)).isEqualTo(detailOf(wrongPasswordResponse));
    }

    @Test
    @DisplayName("forgot-password gives the same answer for a known and an unknown email")
    void forgotPasswordDoesNotRevealExistence() throws Exception {
        String realEmail = verifiedUserEmail();

        String knownResponse = mockMvc.perform(post(AUTH + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(realEmail)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknownResponse = mockMvc.perform(post(AUTH + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(uniqueEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(knownResponse).isEqualTo(unknownResponse);
        // Only the real address actually gets an email.
        verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(realEmail), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    // ==================================================================
    // Password reset
    // ==================================================================

    @Test
    @DisplayName("a reset sets the new password and revokes every existing session")
    void resetPasswordRevokesSessions() throws Exception {
        String email = verifiedUserEmail();

        // Sign in, so there is a live session for the reset to invalidate.
        Cookie sessionBeforeReset = loginAndGetCookie(email, PASSWORD);

        mockMvc.perform(post(AUTH + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(email), anyString(), tokenCaptor.capture(),
                org.mockito.ArgumentMatchers.anyLong());

        String newPassword = "Brand@NewPass2";
        mockMvc.perform(post(AUTH + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}
                                """.formatted(tokenCaptor.getValue(), newPassword)))
                .andExpect(status().isOk());

        // Whoever held that session may be the reason the reset was needed.
        mockMvc.perform(post(AUTH + "/refresh").cookie(sessionBeforeReset))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, newPassword)))
                .andExpect(status().isOk());

        verify(emailService).sendPasswordChangedEmail(
                org.mockito.ArgumentMatchers.eq(email), anyString());
    }

    @Test
    @DisplayName("a reset token cannot be reused")
    void resetTokenIsSingleUse() throws Exception {
        String email = verifiedUserEmail();

        mockMvc.perform(post(AUTH + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(email), anyString(), tokenCaptor.capture(),
                org.mockito.ArgumentMatchers.anyLong());

        String body = """
                {"token":"%s","newPassword":"Another@Pass3"}
                """.formatted(tokenCaptor.getValue());

        mockMvc.perform(post(AUTH + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ==================================================================
    // Authentication and authorisation boundaries
    // ==================================================================

    @Test
    @DisplayName("/auth/me needs a token and returns the caller's permissions")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get(AUTH + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        String email = verifiedUserEmail();
        String accessToken = loginAndGetAccessToken(email, PASSWORD);

        mockMvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("CANDIDATE"))
                .andExpect(jsonPath("$.permissions",
                        org.hamcrest.Matchers.hasItem("SELF_APPLICATION_MANAGE")));
    }

    @Test
    @DisplayName("a garbled or forged bearer token is rejected")
    void rejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get(AUTH + "/me").header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a CANDIDATE cannot reach user administration")
    void permissionIsEnforcedPerEndpoint() throws Exception {
        String accessToken = loginAndGetAccessToken(verifiedUserEmail(), PASSWORD);

        // Authenticated, but holds no USER_READ. 403, not 401.
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("changing the password requires the current one and ends every session")
    void changePassword() throws Exception {
        String email = verifiedUserEmail();
        String accessToken = loginAndGetAccessToken(email, PASSWORD);

        mockMvc.perform(post(AUTH + "/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Wrong@Password9","newPassword":"Fresh@Pass44"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        mockMvc.perform(post(AUTH + "/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"Fresh@Pass44"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());

        // tokenVersion was bumped, so the access token minted a moment ago is
        // already dead - no blacklist involved.
        mockMvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the new password must differ from the current one")
    void rejectsUnchangedPassword() throws Exception {
        String accessToken = loginAndGetAccessToken(verifiedUserEmail(), PASSWORD);

        mockMvc.perform(post(AUTH + "/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("active sessions are listed, and only your own can be revoked")
    void sessionsAreScopedToTheCaller() throws Exception {
        String email = verifiedUserEmail();
        Cookie cookie = loginAndGetCookie(email, PASSWORD);
        String accessToken = loginAndGetAccessToken(email, PASSWORD);

        // Two sign-ins above, so two live sessions.
        mockMvc.perform(get(AUTH + "/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].current", org.hamcrest.Matchers.hasItem(true)));

        // Someone else's session id is reported as not found, so ids cannot be
        // probed for existence.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(AUTH + "/sessions/999999")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout-all revokes every session")
    void logoutAllRevokesEverySession() throws Exception {
        String email = verifiedUserEmail();
        Cookie firstDevice = loginAndGetCookie(email, PASSWORD);
        Cookie secondDevice = loginAndGetCookie(email, PASSWORD);
        String accessToken = loginAndGetAccessToken(email, PASSWORD);

        mockMvc.perform(post(AUTH + "/logout-all")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH + "/refresh").cookie(firstDevice))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(AUTH + "/refresh").cookie(secondDevice))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("every response carries a correlation id, errors included")
    void correlationIdOnErrors() throws Exception {
        mockMvc.perform(get(AUTH + "/me").header("X-Correlation-Id", "trace-auth-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "trace-auth-1"))
                .andExpect(jsonPath("$.correlationId").value("trace-auth-1"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s","rememberMe":false}
                """.formatted(email, password);
    }

    /** Registers a candidate and confirms the address, returning the email. */
    private String verifiedUserEmail() throws Exception {
        Registered registered = registerAndCaptureToken();
        mockMvc.perform(post(AUTH + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(registered.verificationToken())))
                .andExpect(status().isOk());
        return registered.email();
    }

    private Cookie verifiedUserCookie() throws Exception {
        return loginAndGetCookie(verifiedUserEmail(), PASSWORD);
    }

    private Cookie loginAndGetCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String body = mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonValue(body, "accessToken");
    }

    /**
     * Minimal string extraction, to avoid depending on which Jackson major
     * version is on the test classpath - both 2 and 3 are present here.
     */
    private String jsonValue(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private String detailOf(String json) {
        return jsonValue(json, "detail");
    }
}
