package org.example.springbootspacegame.resource;

/**
 * Catalog of resources a ship can carry and a celestial body can yield or buy.
 *
 * <p>All resources extract from {@link org.example.springbootspacegame.ship.ShipStatus#ORBITING}
 * — a ship Chebyshev-adjacent to the body. Per-resource gating disappeared
 * with issue #87 (orbit-only model); which bodies yield which resource is
 * governed entirely by the {@code body_resources} table seeded in V9.
 *
 * <p>Adding a new resource = adding an enum value + (in the seed migration)
 * rows in {@code body_resources} / {@code body_buy_prices}. No schema change.
 */
public enum ResourceKind {

    /** Solid bulk metal. Mined from rocky bodies and asteroids. */
    IRON,

    /** Frozen or liquid. Drilled from ice planets and some asteroids. */
    WATER,

    /** Atmospheric gas. Skimmed from gas giants. */
    HYDROGEN,

    /** Atmospheric noble gas, rare and valuable. Skimmed from gas giants. */
    HELIUM,

    /** Dense, scarce, valuable. Deep-mined from lava planets and some asteroids. */
    RARE_METAL
}
