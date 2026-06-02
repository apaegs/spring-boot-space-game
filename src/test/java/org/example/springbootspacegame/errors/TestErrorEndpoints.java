package org.example.springbootspacegame.errors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller exposing endpoints that deliberately fail in
 * specific ways, so {@link GlobalExceptionHandlerIT} can exercise each
 * branch of {@link GlobalExceptionHandler} including the catch-all
 * {@code Exception} path that no real endpoint should ever hit.
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
}
