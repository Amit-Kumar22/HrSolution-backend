package com.hrsolution.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Arrays;

/**
 * Enables {@code @Async}, used from Phase 2 onwards so that sending an email
 * never blocks an HTTP response.
 *
 * <p>The executor itself is Boot's auto-configured {@code applicationTaskExecutor},
 * sized via {@code spring.task.execution.*} in the properties - no custom
 * executor bean, so the pool can be retuned per environment without a rebuild.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * An exception thrown from a {@code void @Async} method has no caller left to
     * catch it, and would otherwise vanish silently. Logging it here is the
     * difference between "the verification email never arrived" being
     * diagnosable or not.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async method {}.{} failed with arguments {}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        Arrays.toString(params),
                        throwable);
    }
}
