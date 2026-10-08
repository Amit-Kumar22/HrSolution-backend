package com.hrsolution.content;

import com.hrsolution.notification.service.EmailService;
import com.hrsolution.support.AbstractIntegrationTest;
import com.hrsolution.user.entity.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public, unauthenticated surface.
 *
 * <p>Two questions these tests exist to answer: does the marketing site get
 * what it needs without a token, and does it get <em>only</em> that? The
 * second is the one worth the effort - see
 * {@link #publicProfileOmitsBankAndStatutoryDetails()} and
 * {@link #unpublishedContentIsInvisibleToThePublic()}.
 */
class PublicSiteApiIT extends AbstractIntegrationTest {

    private static final String PUBLIC = "/api/v1/public";

    @MockitoBean
    private EmailService emailService;

    // ==================================================================
    // Content reads, no token
    // ==================================================================

    @Test
    @DisplayName("serves the seeded services, industries and categories anonymously")
    void publicContentNeedsNoToken() throws Exception {
        mockMvc.perform(get(PUBLIC + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[*].slug", hasItem("manpower-supply")))
                // Falls back to the page title when no meta title is set.
                .andExpect(jsonPath("$[0].metaTitle").isNotEmpty());

        mockMvc.perform(get(PUBLIC + "/industries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("manufacturing")));

        mockMvc.perform(get(PUBLIC + "/manpower-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("SECURITY_GUARD")))
                .andExpect(jsonPath("$[*].skillLevelLabel", hasItem("Semi-skilled")));
    }

    @Test
    @DisplayName("serves one service page by slug")
    void serviceBySlug() throws Exception {
        mockMvc.perform(get(PUBLIC + "/services/statutory-compliance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("statutory-compliance"))
                .andExpect(jsonPath("$.title").value("Statutory Compliance"))
                .andExpect(jsonPath("$.description").isNotEmpty());
    }

    @Test
    @DisplayName("an unknown slug is a 404, not an empty object")
    void unknownSlugIsNotFound() throws Exception {
        mockMvc.perform(get(PUBLIC + "/services/no-such-service"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("unpublished content is invisible to the public but visible to staff")
    void unpublishedContentIsInvisibleToThePublic() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);

        // Create a draft - published defaults to false.
        mockMvc.perform(post("/api/v1/services")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"secret-draft-service","title":"Draft Service",
                                 "summary":"Not ready for the public yet.","displayOrder":999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(false));

        // Absent from the public list...
        mockMvc.perform(get(PUBLIC + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", not(hasItem("secret-draft-service"))));

        // ...and not reachable by guessing its URL either.
        mockMvc.perform(get(PUBLIC + "/services/secret-draft-service"))
                .andExpect(status().isNotFound());

        // But staff can see it.
        mockMvc.perform(get("/api/v1/services").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("secret-draft-service")));
    }

    @Test
    @DisplayName("the public company profile omits bank and statutory details")
    void publicProfileOmitsBankAndStatutoryDetails() throws Exception {
        String superAdminToken = bearerFor(RoleName.SUPER_ADMIN);

        // Put something sensitive in the profile first, so absence from the
        // public response is meaningful rather than merely unset.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/settings/company")
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Shree Manpower Services Private Limited",
                                 "city":"Pune","state":"Maharashtra","stateCode":"27",
                                 "gstin":"27AABCS1234A1Z5","pan":"AABCS1234A",
                                 "tan":"PNEA12345B",
                                 "bankName":"HDFC Bank","bankAccountNumber":"50100123456789",
                                 "bankIfsc":"HDFC0001234",
                                 "pfEstablishmentCode":"MHBAN1234567",
                                 "esiEstablishmentCode":"31000123450000999",
                                 "ptRegistrationNumber":"27999999999"}
                                """))
                .andExpect(status().isOk())
                // The authenticated view does include them.
                .andExpect(jsonPath("$.bankAccountNumber").value("50100123456789"))
                .andExpect(jsonPath("$.pfEstablishmentCode").value("MHBAN1234567"));

        String publicBody = mockMvc.perform(get(PUBLIC + "/company-profile"))
                .andExpect(status().isOk())
                // Required to be displayed publicly by Indian company law.
                .andExpect(jsonPath("$.gstin").value("27AABCS1234A1Z5"))
                .andExpect(jsonPath("$.pan").value("AABCS1234A"))
                .andExpect(jsonPath("$.legalName").value("Shree Manpower Services Private Limited"))
                // A published bank account is an invitation to invoice fraud.
                .andExpect(jsonPath("$.bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.bankIfsc").doesNotExist())
                .andExpect(jsonPath("$.bankName").doesNotExist())
                // Useful only to someone impersonating the company.
                .andExpect(jsonPath("$.tan").doesNotExist())
                .andExpect(jsonPath("$.pfEstablishmentCode").doesNotExist())
                .andExpect(jsonPath("$.esiEstablishmentCode").doesNotExist())
                .andExpect(jsonPath("$.ptRegistrationNumber").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Belt and braces: the values must not appear anywhere in the body,
        // under any field name.
        org.assertj.core.api.Assertions.assertThat(publicBody)
                .doesNotContain("50100123456789")
                .doesNotContain("HDFC0001234")
                .doesNotContain("MHBAN1234567")
                .doesNotContain("PNEA12345B");
    }

    // ==================================================================
    // Enquiry form
    // ==================================================================

    @Test
    @DisplayName("accepts a minimal enquiry and notifies the sales team")
    void submitEnquiry() throws Exception {
        mockMvc.perform(post(PUBLIC + "/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Bharat Textiles Private Limited",
                                 "contactPerson":"Rajesh Kulkarni",
                                 "phone":"9876543210"}
                                """))
                .andExpect(status().isCreated())
                // Only three fields are mandatory - this form is the top of the
                // sales funnel, and rejecting a lead over a missing start date
                // costs real business.
                .andExpect(jsonPath("$.reference").value(containsString("ENQ-")))
                .andExpect(jsonPath("$.message").value(containsString("received")));

        verify(emailService).sendAdminEnquiryNotification(
                anyString(), anyString(), anyString(), anyString(), any(),
                anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a filled honeypot is stored but silently flagged, and sends no notification")
    void honeypotSubmissionIsFlaggedNotRejected() throws Exception {
        String cleanResponse = submitAndReturnBody("""
                {"companyName":"Genuine Co","contactPerson":"Asha Patil","phone":"9876543211"}
                """);

        String botResponse = submitAndReturnBody("""
                {"companyName":"Bot Co","contactPerson":"Bot","phone":"9876543212",
                 "website":"http://spam.example.com"}
                """);

        // Identical acknowledgement apart from the reference. Telling a bot it
        // was detected only helps it iterate.
        org.assertj.core.api.Assertions.assertThat(messageOf(botResponse))
                .isEqualTo(messageOf(cleanResponse).replace(
                        referenceOf(cleanResponse), referenceOf(botResponse)));

        // The genuine one notified; the flagged one did not, or the
        // notification would be worthless.
        verify(emailService).sendAdminEnquiryNotification(
                org.mockito.ArgumentMatchers.eq(referenceOf(cleanResponse)),
                anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any());
        verify(emailService, never()).sendAdminEnquiryNotification(
                org.mockito.ArgumentMatchers.eq(referenceOf(botResponse)),
                anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any());

        // It is stored, not discarded - a wrongly-flagged lead must be
        // recoverable by a human.
        String adminToken = bearerFor(RoleName.ADMIN);
        mockMvc.perform(get("/api/v1/enquiries?status=SPAM")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].companyName", hasItem("Bot Co")));

        // And it stays out of the default pipeline view.
        mockMvc.perform(get("/api/v1/enquiries")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].companyName", not(hasItem("Bot Co"))));
    }

    @Test
    @DisplayName("an enquiry quoting an unknown category id still succeeds")
    void unknownCategoryIsToleratedNotRejected() throws Exception {
        // A stale dropdown on a cached page must not cost a lead.
        mockMvc.perform(post(PUBLIC + "/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Stale Cache Co","contactPerson":"Someone",
                                 "phone":"9876543213","categoryId":999999,
                                 "otherCategory":"Forklift operators"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").isNotEmpty());
    }

    @Test
    @DisplayName("rejects an enquiry missing the three mandatory fields")
    void enquiryValidation() throws Exception {
        mockMvc.perform(post(PUBLIC + "/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"","contactPerson":"","phone":"12345",
                                 "email":"not-an-email","numberOfWorkers":0,
                                 "requiredFrom":"2020-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItem("companyName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("requiredFrom")));
    }

    // ==================================================================
    // Contact form
    // ==================================================================

    @Test
    @DisplayName("accepts a contact message without revealing an id")
    void submitContactMessage() throws Exception {
        mockMvc.perform(post(PUBLIC + "/contact-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha Patil","email":"asha@example.com",
                                 "subject":"Payroll question","message":"How do you handle PF?"}
                                """))
                .andExpect(status().isCreated())
                // No id or reference: a contact message has no pipeline, and
                // handing out ids anonymously invites enumeration.
                .andExpect(jsonPath("$.reference").doesNotExist())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(emailService).sendAdminContactNotification(
                anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("staff can list, read and reply-flag contact messages")
    void contactInboxWorkflow() throws Exception {
        mockMvc.perform(post(PUBLIC + "/contact-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Inbox Test","email":"inbox@example.com",
                                 "message":"Please call me back."}
                                """))
                .andExpect(status().isCreated());

        String adminToken = bearerFor(RoleName.ADMIN);

        String listBody = mockMvc.perform(get("/api/v1/contact-messages?search=Inbox Test")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Inbox Test"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andReturn().getResponse().getContentAsString();

        long id = idOf(listBody);

        // Reading does NOT mark it read - that is an explicit action, so
        // scrolling a list cannot silently mark everything handled.
        mockMvc.perform(get("/api/v1/contact-messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/contact-messages/" + id + "/replied")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replied").value(true))
                .andExpect(jsonPath("$.read").value(true));
    }

    // ==================================================================
    // Authorisation of the admin side
    // ==================================================================

    @Test
    @DisplayName("editing content requires CONTENT_MANAGE; the enquiry inbox requires ENQUIRY_MANAGE")
    void adminEndpointsAreGuarded() throws Exception {
        String workerToken = bearerFor(RoleName.WORKER);

        mockMvc.perform(get("/api/v1/services").header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/enquiries").header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/contact-messages").header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isForbidden());

        // No token at all is a 401 rather than a 403.
        mockMvc.perform(get("/api/v1/enquiries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("content can be created without the optional displayOrder")
    void createWithoutOptionalPrimitive() throws Exception {
        // Same Jackson 3 primitive issue as the login case: displayOrder is an
        // int, and omitting it used to reject the whole body.
        mockMvc.perform(post("/api/v1/testimonials")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientName":"Rajesh Kulkarni",
                                 "content":"They handled our compliance end to end."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    @DisplayName("a published service's slug cannot be changed")
    void publishedSlugIsProtected() throws Exception {
        String adminToken = bearerFor(RoleName.ADMIN);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/services/1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"renamed-slug","title":"Manpower Supply",
                                 "summary":"Still the same service.","displayOrder":10}
                                """))
                // Renaming a live page breaks every inbound link and discards
                // its search ranking, so it is refused while published.
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detail").value(containsString("inbound links")));
    }

    // ==================================================================
    // SEO
    // ==================================================================

    @Test
    @DisplayName("robots.txt disallows everything while indexing is off")
    void robotsTxtFailsClosed() throws Exception {
        // The test profile leaves app.site.seo-indexing-enabled at its default
        // of false, which is the behaviour a staging host must have.
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disallow: /")))
                .andExpect(content().string(containsString("Indexing is disabled")));
    }

    @Test
    @DisplayName("sitemap.xml is valid XML even when empty")
    void sitemapIsAlwaysValidXml() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<?xml version=\"1.0\"")))
                .andExpect(content().string(containsString(
                        "xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")))
                .andExpect(content().string(containsString("</urlset>")));
    }

    @Test
    @DisplayName("robots.txt and sitemap.xml need no authentication")
    void seoFilesAreAnonymous() throws Exception {
        // Only honoured by crawlers at the domain root, and a crawler has no
        // credentials.
        mockMvc.perform(get("/robots.txt")).andExpect(status().isOk());
        mockMvc.perform(get("/sitemap.xml")).andExpect(status().isOk());
    }


    // ==================================================================
    // File upload and public asset serving
    // ==================================================================

    /** The 8-byte PNG signature followed by a little padding. */
    private static final byte[] TINY_PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Test
    @DisplayName("an uploaded logo is served anonymously at its public URL")
    void logoUploadAndPublicServe() throws Exception {
        String superAdminToken = bearerFor(RoleName.SUPER_ADMIN);

        String uploadBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/v1/settings/company/logo")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                        "file", "logo.png", "image/png", TINY_PNG))
                                .header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoPath").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String storageKey = stringField(uploadBody, "logoPath");

        // The public profile hands the site a ready-to-use URL...
        mockMvc.perform(get(PUBLIC + "/company-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value("/api/v1/public/files/" + storageKey));

        // ...and that URL works with no token, returning the bytes.
        // This route has no @PathVariable - a multi-segment ** wildcard has no
        // name to bind, and asking for one fails on every request.
        byte[] served = mockMvc.perform(get("/api/v1/public/files/" + storageKey))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(served).isEqualTo(TINY_PNG);
    }

    @Test
    @DisplayName("HTML renamed to .png is rejected by content detection")
    void disguisedUploadIsRejected() throws Exception {
        String superAdminToken = bearerFor(RoleName.SUPER_ADMIN);
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        // Neither the .png name nor the declared image/png type gets it through.
        // Serving this back would be stored XSS on this application's origin.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/settings/company/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "logo.png", "image/png", html))
                        .header(HttpHeaders.AUTHORIZATION, superAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(containsString("not supported")));
    }

    @Test
    @DisplayName("a private storage key is not served anonymously")
    void privateKeysAreNotPubliclyServable() throws Exception {
        // The publicAsset flag is the whole security boundary on this route.
        // 404 rather than 403, so the route cannot confirm a document exists.
        mockMvc.perform(get("/api/v1/public/files/worker-documents/2026/10/fabricated.pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("uploading a logo requires SETTINGS_MANAGE")
    void logoUploadIsGuarded() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/settings/company/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "logo.png", "image/png", TINY_PNG))
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.WORKER)))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String submitAndReturnBody(String json) throws Exception {
        return mockMvc.perform(post(PUBLIC + "/enquiries")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String referenceOf(String json) {
        return stringField(json, "reference");
    }

    private String messageOf(String json) {
        return stringField(json, "message");
    }

    private String stringField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private long idOf(String json) {
        String marker = "\"id\":";
        int start = json.indexOf(marker) + marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
