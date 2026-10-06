package com.hrsolution.common.error;

import com.hrsolution.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns every exception escaping a controller into an RFC 7807 {@link ProblemDetail}.
 *
 * <p>Response shape, identical for all errors:
 * <pre>{@code
 * {
 *   "type":          "https://api.hrsolution.local/errors/validation-failed",
 *   "title":         "Validation failed",
 *   "status":        400,
 *   "detail":        "Request contains 2 invalid field(s).",
 *   "instance":      "/api/v1/settings/company",
 *   "errorCode":     "VALIDATION_FAILED",
 *   "timestamp":     "2026-10-06T09:15:42.113Z",
 *   "path":          "/api/v1/settings/company",
 *   "correlationId": "7f3c1e0a-...",
 *   "errors":        [ { "field": "email", "message": "must be a well-formed email address", "rejectedValue": "nope" } ]
 * }
 * }</pre>
 *
 * <p>Two rules hold throughout:
 * <ul>
 *   <li><strong>4xx are logged at WARN without a stack trace</strong> - they are
 *       caller mistakes, not incidents, and stack traces for them are noise.</li>
 *   <li><strong>5xx are logged at ERROR with the stack trace, and the response
 *       body says nothing specific.</strong> Exception messages routinely contain
 *       SQL fragments, table names and file paths; the correlation id is the
 *       bridge between what the user sees and what the log holds.</li>
 * </ul>
 *
 * <p>To add a new error case: define an {@link ErrorCode}, throw a subclass of
 * {@link ApiException}, and nothing here needs to change.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Base URI for the {@code type} field. Not dereferenced, just a stable identifier. */
    private static final String TYPE_BASE = "https://api.hrsolution.local/errors/";

    // ------------------------------------------------------------------
    // Application exceptions
    // ------------------------------------------------------------------

    /**
     * 429. Handled separately from the other {@link ApiException}s because it
     * is the one error that must carry a header - {@code Retry-After} - and a
     * bare {@code ProblemDetail} return value cannot set one.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex,
                                                                 HttpServletRequest request) {
        log.warn("Rate limit exceeded on {} from {}: retry after {}s",
                request.getRequestURI(), request.getRemoteAddr(), ex.getRetryAfterSeconds());

        ProblemDetail problem = problem(
                HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED, ex.getMessage(), request);
        problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    /**
     * Service-raised validation failure. Must be declared before the general
     * {@link ApiException} handler so the {@code errors} array is included -
     * {@code @ExceptionHandler} resolution prefers the most specific type, but
     * this one needs the extra property attached.
     */
    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException ex,
                                               HttpServletRequest request) {
        return validationProblem(ex.getFieldErrors(), request);
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Application error [{}] on {}", ex.getErrorCode(), request.getRequestURI(), ex);
        } else {
            log.warn("Application error [{}] on {}: {}",
                    ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        }
        return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /** Bean Validation failure on an {@code @Valid @RequestBody} argument. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(ApiFieldError.of(
                    fieldError.getField(),
                    fieldError.getDefaultMessage(),
                    fieldError.getRejectedValue()));
        }
        // Class-level constraints (e.g. "end date must be after start date") have
        // no single field; report them against the object they were declared on.
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            errors.add(ApiFieldError.of(globalError.getObjectName(), globalError.getDefaultMessage(), null));
        }
        return validationProblem(errors, request);
    }

    /**
     * Bean Validation failure on individual handler parameters - a
     * {@code @Min}-annotated {@code @RequestParam}, a {@code @NotBlank}
     * {@code @PathVariable}, and so on.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException ex,
                                                   HttpServletRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    errors.add(ApiFieldError.of(
                            name == null ? "request" : name,
                            error.getDefaultMessage(),
                            result.getArgument())));
        });
        return validationProblem(errors, request);
    }

    /** Bean Validation failure raised from a {@code @Validated} service method. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex,
                                                   HttpServletRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath() == null ? "request" : violation.getPropertyPath().toString();
            errors.add(ApiFieldError.of(path, violation.getMessage(), violation.getInvalidValue()));
        }
        return validationProblem(errors, request);
    }

    private ProblemDetail validationProblem(List<? extends ApiFieldError> errors,
                                            HttpServletRequest request) {
        log.warn("Validation failed on {}: {}", request.getRequestURI(), errors);
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request contains %d invalid field(s).".formatted(errors.size()),
                request);
        problem.setProperty("errors", errors);
        return problem;
    }

    // ------------------------------------------------------------------
    // Malformed requests
    // ------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex,
                                              HttpServletRequest request) {
        log.warn("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());
        // The raw Jackson message names Java classes and JSON offsets; not useful
        // to a caller and mildly informative to an attacker.
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or is not valid JSON.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest request) {
        String required = ex.getRequiredType() == null ? "the expected type" : ex.getRequiredType().getSimpleName();
        String detail = "Parameter '%s' must be of type %s.".formatted(ex.getName(), required);
        log.warn("Type mismatch on {}: {}", request.getRequestURI(), detail);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, detail, request);
        problem.setProperty("errors",
                List.of(ApiFieldError.of(ex.getName(), "must be of type " + required, ex.getValue())));
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex,
                                                HttpServletRequest request) {
        String detail = "Required parameter '%s' is missing.".formatted(ex.getParameterName());
        log.warn("Missing parameter on {}: {}", request.getRequestURI(), detail);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, detail, request);
        problem.setProperty("errors",
                List.of(ApiFieldError.of(ex.getParameterName(), "is required", null)));
        return problem;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex,
                                           HttpServletRequest request) {
        String detail = "Required file part '%s' is missing.".formatted(ex.getRequestPartName());
        log.warn("Missing multipart part on {}: {}", request.getRequestURI(), detail);
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, detail, request);
    }

    // ------------------------------------------------------------------
    // Protocol-level
    // ------------------------------------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                   HttpServletRequest request) {
        log.warn("Method {} not supported on {}", ex.getMethod(), request.getRequestURI());
        return problem(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method %s is not supported by this endpoint.".formatted(ex.getMethod()), request);
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    public ProblemDetail handleMediaType(Exception ex, HttpServletRequest request) {
        log.warn("Unsupported media type on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "The request or response media type is not supported by this endpoint.", request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ProblemDetail handleNoHandler(Exception ex, HttpServletRequest request) {
        log.warn("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint exists at this path.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                              HttpServletRequest request) {
        log.warn("Upload too large on {}: {}", request.getRequestURI(), ex.getMessage());
        // CONTENT_TOO_LARGE is the current name for 413; PAYLOAD_TOO_LARGE is
        // deprecated in Spring 7.
        return problem(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum permitted size.", request);
    }

    // ------------------------------------------------------------------
    // Security
    // ------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Unauthenticated request to {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                "Authentication is required to access this resource.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // Logged at WARN with the principal: repeated 403s are a signal worth seeing.
        log.warn("Access denied for '{}' on {} {}",
                currentPrincipal(), request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.", request);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex,
                                             HttpServletRequest request) {
        // Full cause chain to the log, nothing to the caller: the driver message
        // contains table names, column names and index names.
        log.error("Data integrity violation on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.CONFLICT, ErrorCode.DATA_INTEGRITY_VIOLATION,
                "The operation conflicts with existing data or a required reference is missing.",
                request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "This record was changed by someone else. Reload it and try again.", request);
    }

    // ------------------------------------------------------------------
    // Fallback
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the correlationId when reporting this.",
                request);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ProblemDetail problem(HttpStatus status, ErrorCode errorCode,
                                  String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(titleFor(errorCode));
        problem.setType(URI.create(TYPE_BASE + slug(errorCode)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        return problem;
    }

    /** {@code VALIDATION_FAILED} -> {@code "Validation failed"}. */
    private String titleFor(ErrorCode errorCode) {
        String words = errorCode.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** {@code VALIDATION_FAILED} -> {@code "validation-failed"}. */
    private String slug(ErrorCode errorCode) {
        return errorCode.name().toLowerCase().replace('_', '-');
    }

    private String currentPrincipal() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
