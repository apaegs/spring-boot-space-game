package org.example.springbootspacegame.errors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller exposing endpoints that deliberately fail in
 * specific ways, so {@link GlobalExceptionHandlerIT} can exercise each
 * branch of {@link GlobalExceptionHandler} including paths that no real
 * endpoint should reach (the catch-all {@code Exception}) and paths the
 * normal app code converts to friendlier exceptions before they can hit
 * the advice (notably {@link DataIntegrityViolationException}, which
 * {@code AuthService.register} catches and remaps to a 409).
 *
 * <p>Lives under {@code src/test/java} so the routes only exist on the
 * test classpath. They'd be a footgun in prod — an authenticated user
 * could grep the routing table and hit them.
 */
@RestController
@RequestMapping("/api/test-errors")
public class TestErrorEndpoints {

    @GetMapping("/runtime")
    public String runtime() {
        throw new RuntimeException("boom");
    }

    @GetMapping("/illegal-state")
    public String illegalState() {
        throw new IllegalStateException("not configured");
    }

    /**
     * Simulates a DB integrity violation bubbling out of a service. Real app
     * code converts these to a {@code ResponseStatusException(CONFLICT)} at
     * the catch site (see {@code AuthService.register}), so the bare
     * advice branch never actually fires in prod traffic — but it's the
     * safety net for any future code path that forgets to wrap.
     *
     * <p>Cause message intentionally mimics what Postgres + Hibernate would
     * emit so the redaction in {@code GlobalExceptionHandler.onDataIntegrity}
     * has something realistic to elide.
     */
    @GetMapping("/data-integrity")
    public String dataIntegrity() {
        throw new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"users_email_key\""
                        + "\n  Detail: Key (email)=(leaky@example.com) already exists."));
    }

    /**
     * Simulates an authorization decision throwing from a controller (i.e.
     * not the CSRF or unauthenticated paths handled inside the Spring
     * Security filter chain itself). The advice's
     * {@code AccessDeniedException} branch catches it and returns 403.
     */
    @GetMapping("/access-denied")
    public String accessDenied() {
        throw new AccessDeniedException("test-only forbidden");
    }
}
