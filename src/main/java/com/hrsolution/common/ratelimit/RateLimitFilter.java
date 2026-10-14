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
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

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

    /**
     * Path pattern to the rule that guards it. All are POST-only.
     *
     * <p>Patterns rather than literal paths because some guarded endpoints
     * carry a variable - {@code /public/jobs/{slug}/apply} is the first. A
     * pattern with no variables still matches exactly, so the auth entries
     * behave as they always did.
     */
    private Map<PathPattern, AuthProperties.RateLimit.Rule> rulesByPattern() {
        AuthProperties.RateLimit limits = authProperties.getRateLimit();
        return Map.of(
                pattern(ApiPaths.V1 + "/auth/login"), limits.getLogin(),
                pattern(ApiPaths.V1 + "/auth/register/candidate"), limits.getRegister(),
                pattern(ApiPaths.V1 + "/auth/register/client"), limits.getRegister(),
                pattern(ApiPaths.V1 + "/auth/forgot-password"), limits.getForgotPassword(),
                pattern(ApiPaths.V1 + "/auth/resend-verification"), limits.getForgotPassword(),
                pattern(ApiPaths.V1 + "/auth/refresh"), limits.getRefresh(),
                // The public forms. Anonymous and world-reachable, so these are
                // the endpoints most in need of a limit.
                pattern(ApiPaths.PUBLIC_V1 + "/enquiries"), limits.getPublicForm(),
                pattern(ApiPaths.PUBLIC_V1 + "/contact-messages"), limits.getPublicForm(),
                pattern(ApiPaths.PUBLIC_V1 + "/jobs/{slug}/apply"), limits.getPublicForm());
    }

    private static PathPattern pattern(String pattern) {
        return PARSER.parse(pattern);
    }

    private static final PathPatternParser PARSER = new PathPatternParser();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!authProperties.getRateLimit().isEnabled()) {
            return true;
        }
        return !HttpMethod.POST.matches(request.getMethod())
                || matchedPattern(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        PathPattern matched = matchedPattern(request);
        if (matched == null) {
            // shouldNotFilter already excluded this, but a null rule would NPE
            // inside the service, so fail open rather than fail obscurely.
            filterChain.doFilter(request, response);
            return;
        }
        AuthProperties.RateLimit.Rule rule = rulesByPattern().get(matched);

        try {
            rateLimitService.consumeOrThrow(keyFor(matched, request), rule);
        } catch (RateLimitExceededException e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
            // Must not continue the chain - the response is already committed.
            return;
        }

        filterChain.doFilter(request, response);
    }

    private PathPattern matchedPattern(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return rulesByPattern().keySet().stream()
                .filter(candidate -> candidate.matches(path))
                .findFirst()
                .orElse(null);
    }

    /**
     * Namespaced per endpoint, so exhausting one does not block the others.
     *
     * <p>Keyed on the <strong>pattern</strong>, not the concrete path. Keying on
     * the path would give {@code /jobs/guard-pune/apply} and
     * {@code /jobs/helper-nashik/apply} a bucket each, so an attacker could
     * multiply their allowance by the number of open vacancies.
     */
    private String keyFor(PathPattern pattern, HttpServletRequest request) {
        return "ip:" + pattern.getPatternString() + ":" + RequestUtils.clientIp(request);
    }
}
