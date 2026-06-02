package org.example.springbootspacegame.errors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralizes all non-2xx response shaping into a single JSON contract — see
 * {@link ApiErrorResponse} for the wire shape. Without this, Spring's default
 * error machinery emits {@code {timestamp, status, error, message, path}}
 * which is inconsistent with what {@code frontend/src/api/client.ts} expects
 * and leaks framework-specific keys ({@code timestamp}, {@code path}) that
 * the SPA never reads.
 *
 * <p>Branches, in matching order:
 *
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} on a
 *       {@code @RequestBody} failed. Returns 400 with {@code details} mapping
 *       field name → first violation message. The first violation is enough;
 *       per-field "you have N errors on this field" UX is overkill for v1.</li>
 *   <li>{@link DataIntegrityViolationException} — a DB constraint fired
 *       (uniqueness, FK, CHECK). Returns 409. The original SQL message is
 *       intentionally hidden — it leaks column / constraint names that aren't
 *       part of the public API.</li>
 *   <li>{@link CsrfException} — CSRF token missing or invalid. Returns 403
 *       so {@code client.ts} can distinguish "you forgot the token" from
 *       "you're not logged in".</li>
 *   <li>{@link AuthenticationException} — Spring Security's unauthenticated
 *       signal. Returns 401. Catches anything {@code HttpStatusEntryPoint}
 *       didn't already intercept (it covers the unauthenticated-filter path
 *       but not late throws from controllers / services).</li>
 *   <li>{@link AccessDeniedException} — authenticated but forbidden. 403.</li>
 *   <li>{@link ResponseStatusException} — services throw this for deliberate
 *       business-rule failures (not found, conflict, etc.). Passed through
 *       at the chosen status, body normalized to the stable shape.</li>
 *   <li>{@link Exception} catch-all — anything we didn't expect becomes a
 *       500 with an opaque {@code errorId} the player can quote in a bug
 *       report. The actual stack stays in the log; the wire response says
 *       only "Internal server error".</li>
 * </ul>
 *
 * <p>Ordering note: more specific handlers must come first. Spring picks the
 * "narrowest" exception type that matches, but declaration order is the
 * tiebreaker when two are at the same depth in the hierarchy. Keeping
 * specific → generic top-to-bottom makes the dispatch order match the read
 * order, which is the kind of small thing that matters when adding new
 * branches later.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> onUnreadableBody(HttpMessageNotReadableException e) {
        // A malformed JSON body is a client error, not a server error.
        // Don't leak the parser's "Unexpected character at line 5 column 12"
        // detail — clients shouldn't depend on exact Jackson phrasing, and
        // it's noisy in logs.
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Malformed request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> onValidation(MethodArgumentNotValidException e) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ApiErrorResponse body = ApiErrorResponse.withDetails(
                HttpStatus.BAD_REQUEST.value(), "Validation failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> onDataIntegrity(DataIntegrityViolationException e) {
        // Log only the cause's class name — NOT its message. Hibernate /
        // Postgres bake the offending column AND value into the SQLState
        // detail ("Key (email)=(scotty@enterprise.example) already exists")
        // which, with prod's JSON-encoded retained logs, would index PII
        // (the email) plus schema details (the column name) every time a
        // duplicate registration happens. We log the type for triage and
        // rely on requestId / errorId correlation for the rest. The client
        // still gets a generic 409.
        Throwable cause = e.getMostSpecificCause();
        log.warn("DB integrity violation: {}", cause.getClass().getSimpleName());
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "Conflict with existing data");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CsrfException.class)
    public ResponseEntity<ApiErrorResponse> onCsrf(CsrfException e) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "CSRF token missing or invalid");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> onAuthentication(AuthenticationException e) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "Authentication required");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> onAccessDenied(AccessDeniedException e) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> onResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String message = e.getReason() != null ? e.getReason() : "Request failed";
        ApiErrorResponse body = ApiErrorResponse.of(status, message);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> onUnhandled(Exception e) {
        String errorId = UUID.randomUUID().toString();
        // Log with the errorId so a player support flow ("I saw error abc-123")
        // joins straight to the offending stack via grep.
        log.error("Unhandled exception, errorId={}", errorId, e);
        ApiErrorResponse body = ApiErrorResponse.withErrorId(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error",
                errorId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
