package dev.playground.library.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * What the security filters do when they reject a request: no or bad token (401, the entry point)
 * or not allowed (403, the access denied handler). They run before Spring MVC, so
 * {@code @RestControllerAdvice} never sees their exceptions on its own. This class hands them to
 * Spring MVC's exception resolver, and {@code GlobalExceptionHandler} writes the same
 * ProblemDetail as for a 401 or 403 thrown inside a controller. Guide: §5.7 Security.
 */
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityProblemHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        resolve(request, response, ex, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        resolve(request, response, ex, HttpServletResponse.SC_FORBIDDEN);
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, Exception ex, int fallback)
            throws IOException {
        // No handler: the request never reached a controller. Null means nothing handled it, which
        // GlobalExceptionHandler always does; the bare status is only a safety net.
        if (resolver.resolveException(request, response, null, ex) == null) {
            response.sendError(fallback);
        }
    }
}
