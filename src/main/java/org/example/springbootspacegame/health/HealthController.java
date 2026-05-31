package org.example.springbootspacegame.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness check. Says "I'm running" — does <em>not</em> check downstream
 * dependencies (DB, etc.) on purpose. Adding a DB ping would tie liveness to
 * Postgres availability, which is a readiness concern, not liveness.
 *
 * <p>{@code /api/health} is whitelisted in {@link
 * org.example.springbootspacegame.auth.SecurityConfig} so it works without auth —
 * CI smoke tests, frontend startup checks, and platform liveness probes don't
 * carry sessions.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return HealthResponse.OK;
    }
}
