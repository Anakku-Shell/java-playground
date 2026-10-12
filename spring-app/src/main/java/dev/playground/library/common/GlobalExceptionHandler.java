package dev.playground.library.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an RFC 9457 {@code ProblemDetail} ({@code application/problem+json}).
 *
 * <p>{@code @RestControllerAdvice} applies these handlers to every controller. The base class
 * already handles Spring MVC's own exceptions (malformed JSON, 404 for an unknown path, 405, 415...)
 * as ProblemDetails; this class adds the domain exceptions, a catch-all 500 and an {@code errors}
 * map for validation failures. Guide: §5.3 Validation & errors; 401 and 403: §5.7 Security.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * A database constraint rejected a write (unique, foreign key, check). The services check the
     * rules first, so this is mostly the race they cannot see: two requests passing the same check
     * at once (§5.4). The message holds SQL and constraint names, so it goes to the log only. NOT
     * NULL, CHECK and length violations land here too; validation (§5.3) should stop those first, so
     * one showing up in the log is a bug to fix, even though the client sees a 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data.");
    }

    /**
     * Optimistic locking (§5.6): another transaction changed the row (its version) between this
     * transaction's read and its write, so the write matched no row and everything rolled back. The
     * request was fine; the same request sent again runs against the new state, and then either
     * succeeds or fails a business rule with its own message. Hibernate's
     * {@code StaleObjectStateException} reaches us translated by Spring into
     * {@code ObjectOptimisticLockingFailureException}, a subclass of this one. Normal under load, so
     * INFO rather than WARN or ERROR.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.info("Concurrent modification: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The data was changed by another request at the same time. Retry the request.");
    }

    /** {@code ?sort=} outside the endpoint's allow-list ({@code Paging.sanitize}, §5.5). */
    @ExceptionHandler(InvalidSortException.class)
    public ProblemDetail handleInvalidSort(InvalidSortException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * What Spring Data throws when a sort names a property the entity does not have. The paged
     * endpoints check their allow-list first, so this is the safety net for a sort that skips it:
     * the client's mistake, so 400, naming only the requested property (Spring's message also names
     * the entity class). Guide: §5.5.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Cannot sort by '" + ex.getPropertyName() + "': no such property.");
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalService(ExternalServiceException ex) {
        // Not our bug, but worth seeing: WARN with the cause, and a body that hides it.
        log.warn("{}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    /**
     * Not authenticated (§5.7): no token, a token that fails its checks, or a wrong password at
     * login. Spring Security's filters send theirs here through {@code SecurityProblemHandler}. The
     * details stay generic: the decoder's message says which check failed, and a login message that
     * told "unknown email" from "wrong password" would reveal who has an account. A 401 must say how
     * to authenticate: the {@code WWW-Authenticate} header (RFC 6750 for bearer tokens).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        String challenge = "Bearer";
        String detail = "Authentication is required: send a bearer token.";
        if (ex instanceof BadCredentialsException) {
            detail = "Invalid email or password.";
        } else if (ex instanceof OAuth2AuthenticationException oauth2
                && BearerTokenErrorCodes.INVALID_TOKEN.equals(oauth2.getError().getErrorCode())) {
            // The decoder's InvalidBearerTokenException, and a malformed "Authorization: Bearer"
            // header, which the token resolver rejects before any decoding.
            challenge = "Bearer error=\"invalid_token\"";
            detail = "The bearer token is invalid or has expired.";
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail));
    }

    /**
     * Authenticated but not allowed (§5.7): a URL rule or {@code @PreAuthorize} said no. Method
     * security throws from inside the controller or service, so this handler, not a filter, sees it.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You are not allowed to do this.");
    }

    /**
     * Anything else is a bug. The client gets a generic 500 (a message or stack trace could leak
     * internals); the log gets everything. The more specific handlers above and in the base class
     * win over this one: Spring picks the closest exception type.
     *
     * <p>One exception to that: an exception class annotated with {@code @ResponseStatus} declares its
     * own status. Spring MVC would honour it, but only when no {@code @ExceptionHandler} matches, and
     * this one matches everything. So it is honoured here.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        ResponseStatus declared = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (declared != null) {
            ProblemDetail problem = ProblemDetail.forStatus(declared.code());
            if (!declared.reason().isEmpty()) {
                problem.setDetail(declared.reason());
            }
            return problem;
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    /** {@code @Valid @RequestBody} failed: one entry per field in {@code errors}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, List<String>> errors = new TreeMap<>();
        collect(ex.getBindingResult(), errors);
        return withErrors(ex, ex.getBody(), errors, headers, status, request);
    }

    /**
     * Method validation failed: a constraint sits directly on a parameter ({@code @Positive Long id}).
     * When that happens Spring validates the {@code @Valid} body as part of the same step, so body
     * errors arrive here too, as {@link ParameterErrors}. Both paths produce the same shape.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, List<String>> errors = new TreeMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                collect(bodyErrors, errors);
            } else {
                String name = Objects.requireNonNullElse(
                        result.getMethodParameter().getParameterName(),
                        "arg" + result.getMethodParameter().getParameterIndex());
                result.getResolvableErrors().forEach(e -> add(errors, name, e.getDefaultMessage()));
            }
        }
        return withErrors(ex, ex.getBody(), errors, headers, status, request);
    }

    private ResponseEntity<Object> withErrors(
            Exception ex,
            ProblemDetail body,
            Map<String, List<String>> errors,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // Spring's default detail differs per exception ("Invalid request content." / "Validation
        // failure"); the client gets one text for one kind of problem.
        body.setDetail("Invalid request content.");
        // Sorted keys (TreeMap) and sorted messages: the same request always gets the same JSON.
        errors.values().forEach(Collections::sort);
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static void collect(Errors source, Map<String, List<String>> errors) {
        source.getFieldErrors().forEach(e -> add(errors, e.getField(), e.getDefaultMessage()));
        // Class-level constraints (none yet) are reported under the object's name.
        source.getGlobalErrors().forEach(e -> add(errors, e.getObjectName(), e.getDefaultMessage()));
    }

    private static void add(Map<String, List<String>> errors, String key, String message) {
        errors.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    }
}
