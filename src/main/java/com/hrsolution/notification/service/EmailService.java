package com.hrsolution.notification.service;

import com.hrsolution.common.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends templated HTML email.
 *
 * <p><strong>Asynchronous.</strong> SMTP can take seconds, or hang. Sending
 * inline would mean a user's registration request waiting on a mail server, and
 * a mail outage turning registration into a timeout. {@code @Async} hands the
 * send to the task executor and returns immediately, so the HTTP response never
 * depends on SMTP.
 *
 * <p><strong>Retried, then dropped.</strong> Transient SMTP failures are common,
 * so each message is attempted up to {@code app.mail.max-attempts} times with
 * an exponential backoff. After that it is logged at ERROR and abandoned -
 * there is no caller left to inform, and every one of these emails can be
 * re-triggered by the user (resend verification, request another reset).
 *
 * <p>Because sending is asynchronous, a failure cannot be reported to the
 * caller. That shapes the endpoints: {@code /auth/forgot-password} returns the
 * same acknowledgement whether or not the email was accepted, which is also
 * what stops it revealing whether an address is registered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    @Async
    public void sendVerificationEmail(String toEmail, String recipientName, String token) {
        String link = link("verify-email", token);
        send(toEmail, "Confirm your email address", "email/verification",
                Map.of("name", recipientName, "link", link, "token", token,
                        "expiryHours", 24));
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String recipientName, String token,
                                       long expiryMinutes) {
        String link = link("reset-password", token);
        send(toEmail, "Reset your password", "email/password-reset",
                Map.of("name", recipientName, "link", link, "token", token,
                        "expiryMinutes", expiryMinutes));
    }

    /**
     * Confirmation that a password changed.
     *
     * <p>Not merely courteous - it is how a user finds out their account was
     * taken over, since an attacker who changes the password cannot suppress
     * this message.
     */
    @Async
    public void sendPasswordChangedEmail(String toEmail, String recipientName) {
        send(toEmail, "Your password was changed", "email/password-changed",
                Map.of("name", recipientName));
    }

    @Async
    public void sendClientRegistrationReceivedEmail(String toEmail, String recipientName,
                                                    String companyName) {
        send(toEmail, "We have received your registration", "email/client-registration-received",
                Map.of("name", recipientName, "companyName", companyName));
    }

    @Async
    public void sendAdminClientRegistrationNotification(String companyName, String contactName,
                                                        String contactEmail, String contactPhone) {
        send(appProperties.getMail().getAdminRecipient(),
                "New client registration awaiting approval",
                "email/admin-client-registration",
                Map.of("companyName", companyName, "contactName", contactName,
                        "contactEmail", contactEmail, "contactPhone", contactPhone));
    }

    /**
     * Tells the sales team a manpower enquiry has arrived.
     *
     * <p>Only called for submissions the spam guard cleared - a notification
     * that also forwards honeypot hits is a notification people stop reading.
     */
    @Async
    public void sendAdminEnquiryNotification(String reference, String companyName,
                                             String contactPerson, String phone, String email,
                                             String categoryLabel, Integer numberOfWorkers,
                                             String city, String message) {
        // HashMap, not Map.of: several of these are legitimately null on a form
        // that only requires three fields, and Map.of rejects null values.
        Map<String, Object> variables = new HashMap<>();
        variables.put("reference", reference);
        variables.put("companyName", companyName);
        variables.put("contactPerson", contactPerson);
        variables.put("phone", phone);
        variables.put("email", email);
        variables.put("categoryLabel", categoryLabel);
        variables.put("numberOfWorkers", numberOfWorkers);
        variables.put("city", city);
        variables.put("message", message);

        send(appProperties.getMail().getAdminRecipient(),
                "New manpower enquiry %s from %s".formatted(reference, companyName),
                "email/admin-enquiry", variables);
    }

    @Async
    public void sendAdminContactNotification(String name, String email, String phone,
                                             String subject, String message) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", name);
        variables.put("email", email);
        variables.put("phone", phone);
        variables.put("subject", subject);
        variables.put("message", message);

        send(appProperties.getMail().getAdminRecipient(),
                "Website contact message from " + name,
                "email/admin-contact", variables);
    }

    // ------------------------------------------------------------------

    private String link(String path, String token) {
        // The token goes in the query string of a link the user clicks. Keep
        // these links out of anything that logs full URLs with query strings.
        return "%s/%s?token=%s".formatted(
                appProperties.getMail().getBaseUrl().replaceAll("/+$", ""), path, token);
    }

    private void send(String to, String subject, String template, Map<String, Object> variables) {
        AppProperties.Mail settings = appProperties.getMail();

        if (!settings.isEnabled()) {
            log.info("Mail disabled - would have sent '{}' to {} using template {}",
                    subject, to, template);
            return;
        }

        String body = render(template, variables);

        MailException lastFailure = null;
        long delay = settings.getRetryDelayMillis();

        for (int attempt = 1; attempt <= settings.getMaxAttempts(); attempt++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper =
                        new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(settings.getFrom());
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body, true);

                mailSender.send(message);
                log.debug("Sent '{}' to {} on attempt {}", subject, to, attempt);
                return;

            } catch (MailException e) {
                lastFailure = e;
                log.warn("Attempt {}/{} to send '{}' to {} failed: {}",
                        attempt, settings.getMaxAttempts(), subject, to, e.getMessage());
                if (attempt < settings.getMaxAttempts()) {
                    sleep(delay);
                    delay *= 2;
                }
            } catch (Exception e) {
                // A malformed address or template problem will not fix itself,
                // so there is no point retrying.
                log.error("Could not build the message '{}' for {}", subject, to, e);
                return;
            }
        }

        log.error("Gave up sending '{}' to {} after {} attempts",
                subject, to, settings.getMaxAttempts(), lastFailure);
    }

    private String render(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(template, context);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Restore the flag so the pool can shut this worker down cleanly.
            Thread.currentThread().interrupt();
        }
    }
}
