package org.example.springbootspacegame.order;

/**
 * Stored as a VARCHAR in {@code ship_orders.kind} (not a Postgres ENUM type)
 * so adding a new kind is a code-only change: new enum value + new
 * {@link OrderHandler} strategy class, no migration.
 *
 * <p>See DOMAIN.md "ShipOrder" for the per-kind semantics.
 */
public enum OrderKind {
    /** Advance one Chebyshev step toward {@code params.{x, y}} per tick. */
    MOVE,

    /**
     * Extract a resource from a celestial body adjacent to the ship's tile.
     * Multi-tick; the {@code mode} param decides when it stops. Params:
     * {@code {resourceKind: "IRON", mode: "until_cancelled"|{"ticks": N}|{"until_full": true}}}.
     * Cancels if the ship is not {@code ORBITING} (no body in any of the 8
     * adjacent tiles).
     */
    EXTRACT,

    /**
     * Sell all cargo of a resource at a celestial body adjacent to the ship's
     * tile, in exchange for credits. One tick. Cancels if not ORBITING, the
     * adjacent body doesn't buy this resource, or the ship's cargo of that
     * kind is empty. Params: {@code {resourceKind: "IRON"}}.
     */
    SELL
}
