package com.hrsolution.recruitment;

import com.hrsolution.notification.service.EmailService;
import com.hrsolution.support.AbstractIntegrationTest;
import com.hrsolution.user.entity.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jobs, candidates, applications and interviews end to end.
 *
 * <p>Three tests here cover the failures that are invisible to a
 * request-by-request check and only show up when the whole journey is walked:
 *
 * <ul>
 *   <li>{@link #publicJobNeverNamesTheClient()} - the staff and public
 *       responses are separate records precisely so a vacancy cannot be
 *       attributed to the client who raised it. A single careless mapping would
 *       undo that, and every individual endpoint would still return 200.</li>
 *   <li>{@link #candidateCannotReachTheStaffJobEndpoints()} - the leak V7
 *       closed. {@code JOB_READ} looked like the obviously correct permission
 *       for a candidate browsing vacancies, right up to the point where it also
 *       guarded the staff list that names clients and shows drafts.</li>
 *   <li>{@link #schedulingAnInterviewMovesTheApplication()} - the status and
 *       the interviews table have to agree. "Schedule returns 201" passes
 *       whether or not the application actually moved.</li>
 * </ul>
 */
class RecruitmentApiIT extends AbstractIntegrationTest {

    private static final String JOBS = "/api/v1/jobs";
    private static final String PUBLIC_JOBS = "/api/v1/public/jobs";
    private static final String CANDIDATES = "/api/v1/candidates";
    private static final String APPLICATIONS = "/api/v1/applications";

    @MockitoBean
    private EmailService emailService;

    // ==================================================================
    // Jobs
    // ==================================================================

    @Test
    @DisplayName("creates a job as a DRAFT and derives its advertising labels")
    void createJob() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "security-guard-" + unique();

        mockMvc.perform(post(JOBS)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","title":"Security Guard",
                                 "summary":"Day-shift guards for a Pune plant",
                                 "categoryId":%d,"city":"Pune","state":"Maharashtra",
                                 "openings":3,"salaryMin":18000,"salaryMax":22000,
                                 "experienceMinMonths":12,"experienceMaxMonths":60,"shift":"DAY"}
                                """.formatted(slug, categoryId(hr))))
                .andExpect(status().isCreated())
                // Never created straight into OPEN: publishing is a separate,
                // audited decision, so an advert cannot go live before anyone
                // has read it back.
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publiclyVisible").value(false))
                .andExpect(jsonPath("$.salaryRangeLabel")
                        .value("Rs 18000 - Rs 22000 per month"))
                .andExpect(jsonPath("$.experienceLabel").value("1 year - 5 years"))
                .andExpect(jsonPath("$.openings").value(3))
                .andExpect(jsonPath("$.openingsCommitted").value(0))
                .andExpect(jsonPath("$.openingsRemaining").value(3));
    }

    @Test
    @DisplayName("a draft job is invisible on the public careers page")
    void draftJobIsNotAdvertised() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "draft-role-" + unique();
        createJob(hr, slug, 1);

        mockMvc.perform(get(PUBLIC_JOBS + "/" + slug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("the public advert never names the client the role is for")
    void publicJobNeverNamesTheClient() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String admin = bearerFor(RoleName.ADMIN);
        long clientId = createClient(admin);

        String slug = "confidential-role-" + unique();
        String job = mockMvc.perform(post(JOBS)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","title":"Machine Operator","categoryId":%d,
                                 "clientId":%d,"city":"Pune","openings":2}
                                """.formatted(slug, categoryId(hr), clientId)))
                .andExpect(status().isCreated())
                // Staff see the link - they need it to route applications.
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.clientName").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        publish(hr, longField(job, "id"));

        // The internet does not. Clients require that a vacancy not be
        // attributed to them: naming them tells competitors about a plant
        // expansion and tells their workforce about churn.
        String body = mockMvc.perform(get(PUBLIC_JOBS + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Machine Operator"))
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientName").doesNotExist())
                .andExpect(jsonPath("$.siteId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Belt and braces: the client's name must not appear anywhere in the
        // payload, under any key.
        assertThat(body).doesNotContain("Bharat Textiles");
    }

    @Test
    @DisplayName("publishing an advert whose closing date has passed is refused")
    void cannotPublishALapsedAdvert() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "lapsed-" + unique();

        // Create with a future date, then move it into the past the only way
        // the API allows - it refuses a past date on write.
        String job = createJob(hr, slug, 1);
        long jobId = longField(job, "id");

        mockMvc.perform(put(JOBS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","title":"Security Guard","categoryId":%d,
                                 "expiresOn":"%s"}
                                """.formatted(slug, categoryId(hr),
                                LocalDate.now().minusDays(1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detail").value(containsString("in the past")));
    }

    @Test
    @DisplayName("an inverted salary range is refused")
    void invertedSalaryRangeIsRefused() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);

        mockMvc.perform(post(JOBS)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"inverted-%s","title":"Guard","categoryId":%d,
                                 "salaryMin":25000,"salaryMax":18000}
                                """.formatted(unique(), categoryId(hr))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value(containsString("minimum salary is above the maximum")));
    }

    @Test
    @DisplayName("a job with applications cannot be deleted")
    void jobWithApplicationsCannotBeDeleted() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "has-applicants-" + unique();
        long jobId = longField(createJob(hr, slug, 1), "id");
        publish(hr, jobId);
        applyPublicly(slug, "applicant-" + unique() + "@example.com");

        // Deleting would orphan the candidate's history, and from Phase 6 the
        // record of why a worker was hired.
        mockMvc.perform(delete(JOBS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("CLOSED instead")));
    }

    // ==================================================================
    // Applying
    // ==================================================================

    @Test
    @DisplayName("an anonymous application creates a candidate and returns a reference")
    void anonymousApplyCreatesACandidate() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "apply-target-" + unique();
        publish(hr, longField(createJob(hr, slug, 3), "id"));

        String email = "rakesh-" + unique() + "@example.com";
        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                    {"fullName":"Rakesh Shinde","email":"%s",
                                     "phone":"9876543210","city":"Pune",
                                     "totalExperienceMonths":24,"consentGiven":true,
                                     "website":""}
                                    """.formatted(email))
                        ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(containsString("APP-")))
                .andExpect(jsonPath("$.status").value("APPLIED"));

        // The candidate record is created as a side effect, with consent
        // recorded - the DPDP basis for holding the CV at all.
        mockMvc.perform(get(CANDIDATES + "?search=" + email)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hasLogin").value(false))
                .andExpect(jsonPath("$.content[0].applicationCount").value(1));
    }

    @Test
    @DisplayName("applying twice for the same job is a 409 with the email masked")
    void duplicateApplicationIsRefusedWithoutConfirmingTheEmail() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "duplicate-" + unique();
        publish(hr, longField(createJob(hr, slug, 1), "id"));

        String email = "twice-" + unique() + "@example.com";
        applyPublicly(slug, email);

        // Masked because whoever typed the address may not own it. "You already
        // applied" must not double as an account-existence oracle.
        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                    {"fullName":"Rakesh Shinde","email":"%s",
                                     "phone":"9876543210","consentGiven":true}
                                    """.formatted(email))
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.detail").value(containsString("***")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        containsString(email))));
    }

    @Test
    @DisplayName("an application without consent is refused")
    void consentIsRequired() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "needs-consent-" + unique();
        publish(hr, longField(createJob(hr, slug, 1), "id"));

        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                    {"fullName":"No Consent","email":"nc-%s@example.com",
                                     "phone":"9876543210","consentGiven":false}
                                    """.formatted(unique()))
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'consentGiven')]").exists());
    }

    @Test
    @DisplayName("a honeypot submission is accepted and flagged, never rejected")
    void honeypotSubmissionIsStoredAndFlagged() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "honeypot-" + unique();
        publish(hr, longField(createJob(hr, slug, 1), "id"));

        String email = "bot-" + unique() + "@example.com";

        // Accepted, because a false positive here loses a real applicant.
        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                    {"fullName":"Spam Bot","email":"%s","phone":"9876543210",
                                     "consentGiven":true,"website":"http://buy-cheap.example"}
                                    """.formatted(email))
                        ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(containsString("APP-")));

        // Hidden from the default list...
        mockMvc.perform(get(CANDIDATES + "?search=" + email)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // ...but visible, and flagged, when staff ask for it.
        mockMvc.perform(get(CANDIDATES + "?includeSpam=true&search=" + email)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].spamSuspected").value(true));
    }

    @Test
    @DisplayName("a CV that is not really a PDF is rejected on content, not extension")
    void resumeTypeIsCheckedByContent() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "cv-check-" + unique();
        publish(hr, longField(createJob(hr, slug, 1), "id"));

        // An HTML file named resume.pdf. Rejected here rather than opened by
        // whoever downloads it.
        MockMultipartFile fake = new MockMultipartFile("resume", "resume.pdf",
                "application/pdf", "<html><body>not a pdf</body></html>".getBytes());

        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(fake)
                        .file(jsonPart("application", """
                                    {"fullName":"Fake CV","email":"fake-%s@example.com",
                                     "phone":"9876543210","consentGiven":true}
                                    """.formatted(unique()))
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not supported")));
    }

    // ==================================================================
    // The funnel
    // ==================================================================

    @Test
    @DisplayName("the funnel refuses a jump from APPLIED to JOINED and names the legal moves")
    void cannotSkipTheFunnel() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);

        mockMvc.perform(patch(APPLICATIONS + "/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"JOINED","note":"skipping"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                // The message has to say what IS possible, or the user is stuck.
                .andExpect(jsonPath("$.detail").value(containsString("SCREENING")));
    }

    @Test
    @DisplayName("a rejection without a reason is refused")
    void rejectionNeedsAReason() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);

        mockMvc.perform(patch(APPLICATIONS + "/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("reason is required")));
    }

    @Test
    @DisplayName("the funnel trail distinguishes the candidate from staff")
    void trailAttributesEveryMove() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);

        move(hr, applicationId, "SCREENING", "Reading the CV");
        move(hr, applicationId, "SHORTLISTED", "Worth interviewing");

        mockMvc.perform(get(APPLICATIONS + "/" + applicationId + "/detail")
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3))
                // The origin row records the submission: null fromStatus, and
                // not made by staff.
                .andExpect(jsonPath("$.history[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.history[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.history[0].madeByStaff").value(false))
                .andExpect(jsonPath("$.history[1].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$.history[1].toStatus").value("SCREENING"))
                .andExpect(jsonPath("$.history[1].madeByStaff").value(true));
    }

    @Test
    @DisplayName("allowedNextStatuses reflects the current position in the funnel")
    void allowedNextStatusesIsDrivenByPosition() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);

        // The UI drives its status dropdown from this, so it has to be right.
        mockMvc.perform(get(APPLICATIONS + "/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedNextStatuses")
                        .value(org.hamcrest.Matchers.hasItems("SCREENING", "SHORTLISTED",
                                "REJECTED", "ON_HOLD", "WITHDRAWN")))
                .andExpect(jsonPath("$.allowedNextStatuses")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem("JOINED"))))
                .andExpect(jsonPath("$.terminal").value(false));
    }

    @Test
    @DisplayName("openings count commitments, so SELECTED consumes one before anyone joins")
    void openingsCountCommitments() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "openings-" + unique();
        long jobId = longField(createJob(hr, slug, 3), "id");
        publish(hr, jobId);

        long applicationId = applyAndFindId(hr, slug, "committed-" + unique() + "@example.com");
        move(hr, applicationId, "SHORTLISTED", null);
        move(hr, applicationId, "SELECTED", null);

        // A job with three openings and three selected candidates is full even
        // though nobody has started, so the count has to include commitments.
        mockMvc.perform(get(JOBS + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openings").value(3))
                .andExpect(jsonPath("$.openingsCommitted").value(1))
                .andExpect(jsonPath("$.openingsRemaining").value(2));
    }

    @Test
    @DisplayName("the pipeline summary includes every status, even the empty ones")
    void pipelineRendersEveryColumn() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "pipeline-" + unique();
        long jobId = longField(createJob(hr, slug, 2), "id");
        publish(hr, jobId);
        applyPublicly(slug, "pipe-" + unique() + "@example.com");

        // A board that has to invent its own missing keys renders a different
        // set of columns depending on the data, which is worse than useless.
        mockMvc.perform(get(JOBS + "/" + jobId + "/pipeline")
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.countsByStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.countsByStatus.SCREENING").value(0))
                .andExpect(jsonPath("$.countsByStatus.JOINED").value(0))
                .andExpect(jsonPath("$.countsByStatus.WITHDRAWN").value(0));
    }

    // ==================================================================
    // Interviews
    // ==================================================================

    @Test
    @DisplayName("scheduling an interview moves the application to INTERVIEW_SCHEDULED")
    void schedulingAnInterviewMovesTheApplication() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);

        mockMvc.perform(post(APPLICATIONS + "/" + applicationId + "/interviews")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","durationMinutes":45,"mode":"IN_PERSON",
                                 "venueOrLink":"Office 4, MIDC Bhosari, Pune"}
                                """.formatted(soon())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundNumber").value(1))
                .andExpect(jsonPath("$.result").value("PENDING"))
                .andExpect(jsonPath("$.upcoming").value(true));

        // The side effect is the point: a recruiter who has just booked a slot
        // should not also have to drag the card.
        mockMvc.perform(get(APPLICATIONS + "/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$.interviewCount").value(1))
                .andExpect(jsonPath("$.hasUpcomingInterview").value(true));
    }

    @Test
    @DisplayName("INTERVIEW_SCHEDULED cannot be set by hand with no interview booked")
    void statusCannotClaimAnInterviewThatDoesNotExist() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);

        mockMvc.perform(patch(APPLICATIONS + "/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INTERVIEW_SCHEDULED","note":"by hand"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value(containsString("no upcoming interview")));
    }

    @Test
    @DisplayName("a video interview without a joining link is refused")
    void videoInterviewNeedsALink() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);

        // A candidate cannot attend an interview with no location.
        mockMvc.perform(post(APPLICATIONS + "/" + applicationId + "/interviews")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","mode":"VIDEO"}""".formatted(soon())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value(containsString("joining link is required")));
    }

    @Test
    @DisplayName("an interview cannot be scheduled in the past")
    void interviewCannotBeInThePast() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);

        mockMvc.perform(post(APPLICATIONS + "/" + applicationId + "/interviews")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","mode":"PHONE"}"""
                                .formatted(Instant.now().minus(2, ChronoUnit.DAYS))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be scheduled in the past")));
    }

    @Test
    @DisplayName("recording PASS can advance the application, and the result is write-once")
    void interviewResultIsWriteOnce() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);
        long interviewId = scheduleInterview(hr, applicationId);

        mockMvc.perform(patch("/api/v1/interviews/" + interviewId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"PASS","rating":4,"feedback":"Strong candidate",
                                 "advanceApplication":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PASS"))
                .andExpect(jsonPath("$.rating").value(4));

        mockMvc.perform(get(APPLICATIONS + "/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(jsonPath("$.status").value("SELECTED"));

        // Write-once: the feedback is the evidence the candidate was assessed,
        // so revising it after the fact is refused.
        mockMvc.perform(patch("/api/v1/interviews/" + interviewId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"FAIL","feedback":"changed my mind"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("already recorded")));
    }

    @Test
    @DisplayName("a FAIL does not auto-reject the application")
    void failureIsNotAnAutomaticRejection() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);
        long interviewId = scheduleInterview(hr, applicationId);

        mockMvc.perform(patch("/api/v1/interviews/" + interviewId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"FAIL","feedback":"Not right for this role"}"""))
                .andExpect(status().isOk());

        // Rejecting needs a reason and is a person's decision - a candidate who
        // fails one round may suit another role, which is what ON_HOLD is for.
        mockMvc.perform(get(APPLICATIONS + "/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(jsonPath("$.status").value("INTERVIEW_SCHEDULED"));
    }

    @Test
    @DisplayName("cancelling the only interview returns the application to SHORTLISTED")
    void cancellingTheLastInterviewWalksTheStatusBack() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);
        move(hr, applicationId, "SHORTLISTED", null);
        long interviewId = scheduleInterview(hr, applicationId);

        mockMvc.perform(delete("/api/v1/interviews/" + interviewId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isNoContent());

        // Otherwise the status would keep asserting a meeting that is not
        // going to happen.
        mockMvc.perform(get(APPLICATIONS + "/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(jsonPath("$.status").value("SHORTLISTED"))
                .andExpect(jsonPath("$.hasUpcomingInterview").value(false));
    }

    // ==================================================================
    // Authorisation
    // ==================================================================

    @Test
    @DisplayName("a candidate cannot reach the staff job endpoints")
    void candidateCannotReachTheStaffJobEndpoints() throws Exception {
        String candidate = bearerFor(RoleName.CANDIDATE);
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long jobId = longField(createJob(hr, "staff-only-" + unique(), 1), "id");

        // The leak V7 closed. JOB_READ looked like the obviously right
        // permission for a candidate browsing vacancies - until it also guarded
        // the staff list, which names the client, shows drafts, and reports how
        // many people have applied.
        mockMvc.perform(get(JOBS).header(HttpHeaders.AUTHORIZATION, candidate))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(JOBS + "/" + jobId).header(HttpHeaders.AUTHORIZATION, candidate))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(APPLICATIONS).header(HttpHeaders.AUTHORIZATION, candidate))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CANDIDATES).header(HttpHeaders.AUTHORIZATION, candidate))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/interviews").header(HttpHeaders.AUTHORIZATION, candidate))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an operations manager cannot create or edit job adverts")
    void operationsCannotRunRecruitment() throws Exception {
        String ops = bearerFor(RoleName.OPERATIONS_MANAGER);

        // Operations raise the demand for workers; recruitment fills it. The
        // separation is deliberate.
        mockMvc.perform(post(JOBS)
                        .header(HttpHeaders.AUTHORIZATION, ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"ops-attempt","title":"Guard","categoryId":1}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the careers page and apply form need no authentication")
    void careersPageIsAnonymous() throws Exception {
        mockMvc.perform(get(PUBLIC_JOBS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ==================================================================
    // The candidate's own portal
    // ==================================================================

    @Test
    @DisplayName("a candidate sees their own application without the panel's write-up")
    void candidatePortalHidesStaffOnlyFields() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String candidateEmail = createUser(RoleName.CANDIDATE);
        String candidateToken = "Bearer " + loginForAccessToken(candidateEmail, TEST_PASSWORD);

        String slug = "portal-" + unique();
        publish(hr, longField(createJob(hr, slug, 2), "id"));
        long jobId = jobIdBySlug(hr, slug);

        mockMvc.perform(post("/api/v1/me/applications/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coverNote":"Keen to join","expectedSalary":19500}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(containsString("APP-")));

        String body = mockMvc.perform(get("/api/v1/me/applications")
                        .header(HttpHeaders.AUTHORIZATION, candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statusLabel").value("Application received"))
                .andExpect(jsonPath("$[0].withdrawable").value(true))
                .andReturn().getResponse().getContentAsString();

        // A candidate is entitled to know where they stand, not to read the
        // panel's assessment or learn which recruiter owns them.
        assertThat(body)
                .doesNotContain("feedback")
                .doesNotContain("rating")
                .doesNotContain("assignedTo")
                .doesNotContain("rejectionReason");
    }

    @Test
    @DisplayName("a candidate cannot read another candidate's application")
    void candidateCannotReadSomeoneElses() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long someoneElse = anApplication(hr);

        String intruderEmail = createUser(RoleName.CANDIDATE);
        String intruder = "Bearer " + loginForAccessToken(intruderEmail, TEST_PASSWORD);

        // 404 rather than 403, so the API never confirms that an id exists.
        mockMvc.perform(get("/api/v1/me/applications/" + someoneElse)
                        .header(HttpHeaders.AUTHORIZATION, intruder))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a candidate can withdraw once, and the trail records that they did it")
    void candidateCanWithdrawTheirOwnApplication() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String candidateEmail = createUser(RoleName.CANDIDATE);
        String candidateToken = "Bearer " + loginForAccessToken(candidateEmail, TEST_PASSWORD);

        String slug = "withdraw-" + unique();
        publish(hr, longField(createJob(hr, slug, 1), "id"));
        long jobId = jobIdBySlug(hr, slug);

        String applied = mockMvc.perform(post("/api/v1/me/applications/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long applicationId = longField(applied, "applicationId");

        mockMvc.perform(patch("/api/v1/me/applications/" + applicationId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Accepted another offer"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.terminal").value(true));

        // Terminal, so a second attempt is refused rather than silently
        // succeeding.
        mockMvc.perform(patch("/api/v1/me/applications/" + applicationId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get(APPLICATIONS + "/" + applicationId + "/detail")
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[1].toStatus").value("WITHDRAWN"))
                // Not staff: the candidate acted on their own application, and
                // that distinction is what makes the trail auditable.
                .andExpect(jsonPath("$.history[1].madeByStaff").value(false));
    }

    @Test
    @DisplayName("a guest application is adopted when that person registers")
    void guestApplicationIsAdoptedOnRegistration() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String slug = "adopt-" + unique();
        publish(hr, longField(createJob(hr, slug, 2), "id"));

        // Apply as a guest first, using the address a login will later have.
        String email = createUser(RoleName.CANDIDATE);
        applyPublicly(slug, email);

        // Signing in and opening the portal shows that earlier application,
        // resolved by email. Note this is a READ: it must not create or link
        // anything, because the read paths run in a readOnly transaction where
        // Hibernate never flushes - a link written here would be silently
        // discarded, which is exactly the bug this behaviour replaced.
        String token = "Bearer " + loginForAccessToken(email, TEST_PASSWORD);
        mockMvc.perform(get("/api/v1/me/applications")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobSlug").value(slug));

        // One candidate, not two: the guest row was reused rather than
        // duplicated, which is the whole point of email being the identity key.
        mockMvc.perform(get(CANDIDATES + "?search=" + email)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // The link to the login is established by the first write.
        mockMvc.perform(put("/api/v1/me/candidate-profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Now Registered","consentGiven":true}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get(CANDIDATES + "?search=" + email)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hasLogin").value(true));
    }

    @Test
    @DisplayName("an anonymous application cannot overwrite a registered candidate's details")
    void anonymousApplicationCannotTamperWithARegisteredProfile() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String email = createUser(RoleName.CANDIDATE);
        String token = "Bearer " + loginForAccessToken(email, TEST_PASSWORD);

        // Establish the profile, owned by a login.
        mockMvc.perform(put("/api/v1/me/candidate-profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Genuine Owner","phone":"9811111111",
                                 "city":"Pune","consentGiven":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Genuine Owner"));

        // Now an anonymous form quotes that address with different details.
        String slug = "tamper-" + unique();
        publish(hr, longField(createJob(hr, slug, 2), "id"));

        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                    {"fullName":"Impostor","email":"%s","phone":"9899999999",
                                     "city":"Nagpur","consentGiven":true}
                                    """.formatted(email))
                        ))
                .andExpect(status().isCreated());

        // The application is attached - one person, one pipeline record - but
        // nothing on the profile moved. Without this rule, anyone who knew an
        // address could rewrite that person's name, phone and CV.
        mockMvc.perform(get("/api/v1/me/candidate-profile")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Genuine Owner"))
                .andExpect(jsonPath("$.phone").value("9811111111"))
                .andExpect(jsonPath("$.city").value("Pune"));
    }

    // ==================================================================
    // Candidates
    // ==================================================================

    @Test
    @DisplayName("a second candidate with the same email is refused, pointing at the first")
    void candidateEmailIsTheIdentityKey() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String email = "dup-" + unique() + "@example.com";

        mockMvc.perform(post(CANDIDATES)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rakesh Shinde","email":"%s","phone":"9876543210",
                                 "consentGiven":true}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("WALK_IN"))
                .andExpect(jsonPath("$.consentGiven").value(true));

        // Someone who applies four times over two years must be one candidate
        // with four applications, or the recruiter cannot see they were
        // interviewed and rejected last year.
        mockMvc.perform(post(CANDIDATES)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rakesh S","email":"%s","consentGiven":true}"""
                                .formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    @DisplayName("a candidate cannot be created without consent")
    void candidateNeedsConsent() throws Exception {
        mockMvc.perform(post(CANDIDATES)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(RoleName.HR_RECRUITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"No Consent","email":"nc-%s@example.com"}"""
                                .formatted(unique())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Consent")));
    }

    @Test
    @DisplayName("a candidate's email cannot be changed by an update")
    void candidateEmailIsImmutable() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        String email = "immutable-" + unique() + "@example.com";

        String body = mockMvc.perform(post(CANDIDATES)
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rakesh Shinde","email":"%s","consentGiven":true}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Changing it would silently re-point an application history onto a
        // different person.
        mockMvc.perform(put(CANDIDATES + "/" + longField(body, "id"))
                        .header(HttpHeaders.AUTHORIZATION, hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rakesh Shinde","email":"someone-else@example.com",
                                 "consentGiven":true}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be changed")));
    }

    @Test
    @DisplayName("a candidate who has joined cannot be deleted")
    void joinedCandidateIsRetained() throws Exception {
        String hr = bearerFor(RoleName.HR_RECRUITER);
        long applicationId = anApplication(hr);

        move(hr, applicationId, "SHORTLISTED", null);
        move(hr, applicationId, "SELECTED", null);
        move(hr, applicationId, "JOINED", "Started on site");

        long candidateId = longField(mockMvc.perform(get(APPLICATIONS + "/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andReturn().getResponse().getContentAsString(), "candidateId");

        // The recruitment record is the evidence the hire was vetted, which
        // Phase 10 may be asked to produce. That obligation outlives the
        // consent to hold marketing data.
        mockMvc.perform(delete(CANDIDATES + "/" + candidateId)
                        .header(HttpHeaders.AUTHORIZATION, hr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("has joined")));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String createJob(String token, String slug, int openings) throws Exception {
        return mockMvc.perform(post(JOBS)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","title":"Security Guard","categoryId":%d,
                                 "city":"Pune","openings":%d}
                                """.formatted(slug, categoryId(token), openings)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void publish(String token, long jobId) throws Exception {
        mockMvc.perform(patch(JOBS + "/" + jobId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}"""))
                .andExpect(status().isOk());
    }

    private void applyPublicly(String slug, String email) throws Exception {
        mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                    {"fullName":"Applicant","email":"%s","phone":"9876543210",
                                     "consentGiven":true}
                                    """.formatted(email))
                        ))
                .andExpect(status().isCreated());
    }

    /** Creates a job, publishes it, applies to it, and returns the application id. */
    private long anApplication(String token) throws Exception {
        String slug = "funnel-" + unique();
        publish(token, longField(createJob(token, slug, 3), "id"));
        return applyAndFindId(token, slug, "applicant-" + unique() + "@example.com");
    }

    private long applyAndFindId(String token, String slug, String email) throws Exception {
        String body = mockMvc.perform(multipart(PUBLIC_JOBS + "/" + slug + "/apply")
                        .file(jsonPart("application", """
                                {"fullName":"Applicant","email":"%s","phone":"9876543210",
                                 "consentGiven":true}
                                """.formatted(email))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "applicationId");
    }

    private void move(String token, long applicationId, String status, String note)
            throws Exception {
        String body = note == null
                ? "{\"status\":\"%s\"}".formatted(status)
                : "{\"status\":\"%s\",\"note\":\"%s\"}".formatted(status, note);

        mockMvc.perform(patch(APPLICATIONS + "/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private long scheduleInterview(String token, long applicationId) throws Exception {
        String body = mockMvc.perform(post(APPLICATIONS + "/" + applicationId + "/interviews")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","mode":"PHONE"}""".formatted(soon())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    private long jobIdBySlug(String token, String slug) throws Exception {
        String body = mockMvc.perform(get(JOBS + "?search=" + slug)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    private long createClient(String adminToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Bharat Textiles Private Limited %s",
                                 "tradeName":"Bharat Textiles",
                                 "billingCity":"Pune","billingStateCode":"27"}
                                """.formatted(unique())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    private long categoryId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/manpower-categories")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    /**
     * A slot two hours out: in the future, so scheduling accepts it, but inside
     * the grace window that lets an early-finishing interview be written up.
     */
    private static String soon() {
        return Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(name, null, MediaType.APPLICATION_JSON_VALUE,
                json.getBytes());
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
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
