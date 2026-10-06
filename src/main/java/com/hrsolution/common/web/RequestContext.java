package com.hrsolution.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The request metadata that services need to record: who called, from where.
 *
 * <p>Exists so that services never take an {@link HttpServletRequest}
 * parameter. Controllers translate the servlet request into this value and pass
 * it down, which keeps the services testable with a plain record and free of
 * any dependency on the servlet API.
 *
 * @param ipAddress     caller's IP, or null when unavailable
 * @param userAgent     caller's User-Agent, truncated to the column width
 * @param correlationId the request's correlation id, tying an audit row back to
 *                      the log lines and the response the user saw
 */
public record RequestContext(String ipAddress, String userAgent, String correlationId) {

    public static RequestContext from(HttpServletRequest request) {
        return new RequestContext(
                RequestUtils.clientIp(request),
                RequestUtils.userAgent(request),
                CorrelationIdFilter.current());
    }

    /** For background jobs and tests, where there is no inbound request. */
    public static RequestContext none() {
        return new RequestContext(null, null, null);
    }
}
