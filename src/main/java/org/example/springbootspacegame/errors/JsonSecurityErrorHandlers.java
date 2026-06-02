package org.example.springbootspacegame.errors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Spring Security's filter chain rejects bad requests <i>before</i> any
 * {@code @RestControllerAdvice} can see them — auth missing → its
 * {@link AuthenticationEntryPoint} fires; auth present but forbidden → its
 * {@link AccessDeniedHandler} fires; CSRF check fails → routes through the
 * {@code AccessDeniedHandler} too. Default implementations set only an HTTP
 * status with an empty body, which leaves the SPA reading "{}" or worse.
 *
 * <p>These two handlers write {@link ApiErrorResponse} JSON to keep the wire
 * shape promise honored for every error path, not just the ones that reach
 * controller code. Wired in {@code SecurityConfig#exceptionHandling}.
 */
public final class JsonSecurityErrorHandlers {

    private JsonSecurityErrorHandlers() {
        // namespace only
    }

    /** 401 entry point for unauthenticated requests. */
    public static AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) ->
                writeError(mapper, res, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    /** 403 handler for authenticated-but-forbidden (incl. CSRF rejection). */
    public static AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) -> {
            // Disambiguate CSRF from "you're authenticated but not allowed". The
            // SPA already treats both as "retry won't help" but the message helps
            // anyone reading the response body manually (curl, etc.).
            String message = ex instanceof org.springframework.security.web.csrf.CsrfException
                    ? "CSRF token missing or invalid"
                    : "Access denied";
            writeError(mapper, res, HttpStatus.FORBIDDEN, message);
        };
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse res, HttpStatus status, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ApiErrorResponse.of(status.value(), message));
    }
}
