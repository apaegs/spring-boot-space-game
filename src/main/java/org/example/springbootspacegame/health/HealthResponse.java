package org.example.springbootspacegame.health;

/**
 * Tiny DTO so the shape is fixed and JSON-typed (vs returning a {@code Map} that
 * could grow accidental fields).
 */
public record HealthResponse(String status) {

    static final HealthResponse OK = new HealthResponse("ok");
}
