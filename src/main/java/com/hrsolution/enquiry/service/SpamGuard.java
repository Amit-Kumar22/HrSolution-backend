package com.hrsolution.enquiry.service;

import com.hrsolution.common.web.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Spam checks for the public forms.
 *
 * <p>Three cheap, layered signals. None is individually reliable, which is why
 * there are three and why a hit is <em>flagged rather than rejected</em> — see
 * below.
 *
 * <h2>Honeypot</h2>
 * The form carries a field a human never sees (hidden by CSS) and therefore
 * never fills. Most bots fill every input they find, so a non-empty value is a
 * strong bot signal with no cost to real users — no captcha, no friction,
 * nothing to fail.
 *
 * <h2>Submission speed</h2>
 * The form records when it was rendered. A human reading and completing a
 * manpower requirement form takes longer than a couple of seconds; a script
 * posts immediately.
 *
 * <h2>Content heuristics</h2>
 * Link counts and the usual spam vocabulary. The weakest of the three, so it
 * only ever contributes to a flag.
 *
 * <h2>Why flag and not reject</h2>
 * A false positive on this form costs a real sales lead. So a suspected
 * submission is still stored, marked {@code SPAM}, and simply kept out of the
 * admin's default view and the notification email. A human can recover it; a
 * rejected one is gone, and the visitor is told nothing useful. The honeypot is
 * the single exception where rejection would be safe, and even there flagging
 * keeps the data for tuning.
 */
@Slf4j
@Service
public class SpamGuard {

    /** Minimum plausible time for a human to complete a form, in milliseconds. */
    private static final long MIN_HUMAN_FILL_MILLIS = 2_000;

    /** More links than this in a short message is almost always spam. */
    private static final int MAX_LINKS = 2;

    private static final List<String> SPAM_PHRASES = List.of(
            "seo service", "buy now", "casino", "viagra", "crypto investment",
            "forex", "loan offer", "work from home guarantee", "click here now",
            "limited time offer", "make money fast", "bitcoin");

    /**
     * The outcome of the checks.
     *
     * @param suspected whether to store the submission flagged as spam
     * @param reason    why, for the log and the admin screen
     */
    public record Verdict(boolean suspected, String reason) {

        static Verdict clean() {
            return new Verdict(false, null);
        }

        static Verdict spam(String reason) {
            return new Verdict(true, reason);
        }
    }

    /**
     * Assesses a public form submission.
     *
     * @param honeypotValue   the hidden field. Non-empty means a bot filled it.
     * @param formRenderedAt  epoch millis the form was served, if the client
     *                        sent it. Null simply skips the timing check.
     * @param freeTextFields  message, subject and similar, for the content checks
     */
    public Verdict assess(String honeypotValue,
                          Long formRenderedAt,
                          RequestContext context,
                          String... freeTextFields) {

        if (honeypotValue != null && !honeypotValue.isBlank()) {
            log.info("Honeypot triggered from ip={} ua={}", context.ipAddress(), context.userAgent());
            return Verdict.spam("Honeypot field was filled");
        }

        if (formRenderedAt != null) {
            long elapsed = System.currentTimeMillis() - formRenderedAt;
            // Negative means a clock skew or a forged value; neither is a
            // reason to flag a lead, so only the too-fast case counts.
            if (elapsed >= 0 && elapsed < MIN_HUMAN_FILL_MILLIS) {
                log.info("Form submitted in {}ms from ip={} - too fast for a human",
                        elapsed, context.ipAddress());
                return Verdict.spam("Submitted %dms after the form was rendered".formatted(elapsed));
            }
        }

        for (String text : freeTextFields) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);

            int links = countOccurrences(lower, "http://") + countOccurrences(lower, "https://")
                    + countOccurrences(lower, "www.");
            if (links > MAX_LINKS) {
                log.info("Content flagged: {} links from ip={}", links, context.ipAddress());
                return Verdict.spam("Contains %d links".formatted(links));
            }

            for (String phrase : SPAM_PHRASES) {
                if (lower.contains(phrase)) {
                    log.info("Content flagged: phrase '{}' from ip={}", phrase, context.ipAddress());
                    return Verdict.spam("Contains the phrase '%s'".formatted(phrase));
                }
            }
        }

        return Verdict.clean();
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
