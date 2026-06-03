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
     * Validate that the ship is on a celestial body's tile. Status derivation
     * reads the body's {@code kind} to decide whether the ship is
     * {@code LANDED} (most kinds) or {@code ORBITING} ({@code GAS_GIANT}).
     * The handler itself just checks "is there a body here?" — the kind
     * resolution is read-time only, not stored.
     */
    LAND,

    /**
     * Leave the current body. One tick, always succeeds if the ship was at a
     * body — but a TAKE_OFF on an already-IDLE/MOVING ship cancels.
     * After a completed TAKE_OFF, status derivation returns {@code IDLE}
     * regardless of the ship's current position.
     */
    TAKE_OFF,

    /**
     * Extract a resource from the body the ship is currently at. Multi-tick;
     * the {@code mode} param decides when it stops (until cancelled, after N
     * ticks, or when cargo is full). Params:
     * {@code {resourceKind: "IRON", mode: "until_cancelled"|{"ticks": N}|{"until_full": true}}}.
     */
    EXTRACT,

    /**
     * Sell all cargo of a resource at the body the ship is at, in exchange for
     * credits. One tick. Cancels if the body doesn't buy this resource or the
     * ship's cargo of that kind is empty. Params: {@code {resourceKind: "IRON"}}.
     */
    SELL
}
