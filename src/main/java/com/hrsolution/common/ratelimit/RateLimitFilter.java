package com.hrsolution.common.ratelimit;

import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.error.RateLimitExceededException;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Map;

/**
 * Per-IP rate limiting on the unauthenticated auth endpoints.
 *
 * <p>Only IP-keyed limits live here, because a filter would have to consume the
 * request body to see the email - which would leave nothing for the controller
 * to read. Per-email limits are applied inside {@code AuthService}, where the
 * parsed request is already available.
 *
 * <p>A rejection is routed through {@link HandlerExceptionResolver} rather than
 * written here by hand, so a 429 from this filter has exactly the same
 * ProblemDetail shape as every other error - built in the one place that builds
 * error responses, {@code GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;
    private final RateLimitService rateLimitService;

    /**
     * Spring MVC's own resolver, which dispatches to {@code @RestControllerAdvice}.
     * Qualified by name because several {@code HandlerExceptionResolver} beans
     * exist in a web context.
     */
    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver handlerExceptionResolver;

    /** Request path to the rule that guards it. All are POST-only. */
    private Map<String, AuthProperties.RateLimit.Rule> rulesByPath() {
        AuthProperties.RateLimit limits = authProperties.getRateLimit();
        return Map.of(
                ApiPaths.V1 + "/auth/login", limits.getLogin(),
                ApiPaths.V1 + "/auth/register/candidate", limits.getRegister(),
                ApiPaths.V1 + "/auth/register/client", limits.getRegister(),
                ApiPaths.V1 + "/auth/forgot-password", limits.getForgotPassword(),
                ApiPaths.V1 + "/auth/resend-verification", limits.getForgotPassword(),
                ApiPaths.V1 + "/auth/refresh", limits.getRefresh(),
                // The public forms. Anonymous and world-reachable, so these are
                // the endpoints most in need of a limit.
                ApiPaths.PUBLIC_V1 + "/enquiries", limits.getPublicForm(),
                ApiPaths.PUBLIC_V1 + "/contact-messages", limits.getPublicForm());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!authProperties.getRateLimit().isEnabled()) {
            return true;
        }
        return !HttpMethod.POST.matches(request.getMethod())
                || !rulesByPath().containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        AuthProperties.RateLimit.Rule rule = rulesByPath().get(path);

        try {
            rateLimitService.consumeOrThrow(keyFor(path, request), rule);
        } catch (RateLimitExceededException e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
            // Must not continue the chain - the response is already committed.
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Namespaced per endpoint, so exhausting one does not block the others. */
    private String keyFor(String path, HttpServletRequest request) {
        return "ip:" + path + ":" + RequestUtils.clientIp(request);
    }
}
