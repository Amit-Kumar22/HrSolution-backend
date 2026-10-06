package com.hrsolution.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every request with a correlation id so that all log lines produced while
 * handling it can be grepped together, and so a user reporting a failure can
 * quote the id from the error response.
 *
 * <p>Reuses an inbound {@code X-Correlation-Id} header when the caller supplies
 * one (useful behind a gateway or when chaining services), otherwise generates
 * one. The id is placed in the SLF4J {@link MDC} under {@value #MDC_KEY} - the
 * logging pattern in {@code application.properties} prints it - and echoed back
 * on the response.
 *
 * <p>Runs first in the filter chain so that even authentication failures are
 * logged with an id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Cap on an inbound id, so a hostile caller cannot bloat every log line. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Thread-pool threads are recycled; leaving the value behind would
            // mislabel the next request handled by this thread.
            MDC.remove(MDC_KEY);
        }
    }

    private String resolve(String inbound) {
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = inbound.trim();
        // Strip anything that could forge a log line (CR/LF) or inflate it.
        String sanitised = trimmed.replaceAll("[^A-Za-z0-9._\\-]", "");
        if (sanitised.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sanitised.length() > MAX_LENGTH ? sanitised.substring(0, MAX_LENGTH) : sanitised;
    }

    /** The current request's correlation id, for use when building responses. */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "n/a" : id;
    }
}
