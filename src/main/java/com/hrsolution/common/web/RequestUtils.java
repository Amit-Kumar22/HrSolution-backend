package com.hrsolution.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Small helpers for the request metadata recorded against sessions and audit
 * rows.
 */
public final class RequestUtils {

    /** Matches the {@code user_agent} column width. */
    private static final int USER_AGENT_MAX_LENGTH = 300;

    private RequestUtils() {
    }

    /**
     * The caller's IP address.
     *
     * <p>Deliberately {@code getRemoteAddr()} and <strong>not</strong> a
     * hand-rolled {@code X-Forwarded-For} parse. A client can send any
     * {@code X-Forwarded-For} value it likes, so trusting the header directly
     * would let an attacker rotate the header to defeat per-IP rate limiting and
     * forge the IP written into the audit trail.
     *
     * <p>Behind a reverse proxy or load balancer, set
     * {@code server.forward-headers-strategy=FRAMEWORK} (or {@code NATIVE} for
     * Tomcat's own valve). Spring then applies the header before the request
     * reaches application code, using the trusted-proxy rules configured on the
     * container - which is the correct place for that decision.
     */
    public static String clientIp(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    /** User-Agent, truncated to the column width. Null-safe. */
    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > USER_AGENT_MAX_LENGTH
                ? userAgent.substring(0, USER_AGENT_MAX_LENGTH)
                : userAgent;
    }
}
