package org.example.springbootspacegame.order;

/**
 * Stored as a VARCHAR in {@code ship_orders.kind} (not a Postgres ENUM type)
 * so adding a new kind is a code-only change: new enum value + new
 * {@link OrderHandler} strategy class, no migration.
 *
 * <p>See DOMAIN.md "ShipOrder" for the per-kind semantics.
 */
public enum OrderKind {
    MOVE,
    LAND
}
