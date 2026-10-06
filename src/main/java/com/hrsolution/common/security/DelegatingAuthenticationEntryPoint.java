package com.hrsolution.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Returns a 401 as a ProblemDetail instead of Spring Security's default empty
 * body.
 *
 * <p>Rather than serialising JSON here, the exception is handed to Spring MVC's
 * {@link HandlerExceptionResolver}, which dispatches it to
 * {@code GlobalExceptionHandler}. That keeps the promise that error responses
 * are constructed in exactly one place - so a 401 raised deep in the security
 * filter chain carries the same {@code errorCode}, {@code timestamp} and
 * {@code correlationId} as a 404 from a controller, with no duplicated code to
 * drift out of step.
 */
@Component
@RequiredArgsConstructor
public class DelegatingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }
}
