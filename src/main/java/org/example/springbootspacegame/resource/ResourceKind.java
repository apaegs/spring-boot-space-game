package org.example.springbootspacegame.resource;

import org.example.springbootspacegame.ship.ShipStatus;

/**
 * Catalog of resources a ship can carry and a celestial body can yield or buy.
 *
 * <p>Each kind declares its {@link #extractionState() extraction state} — the
 * ship status required to extract this resource. Solids and ices are extracted
 * from the surface ({@link ShipStatus#LANDED}); gases are extracted from a body's
 * atmosphere ({@link ShipStatus#ORBITING}).
 *
 * <p>The {@code LAND} order handler (PR 2) resolves to either status depending
 * on the body kind; the {@code EXTRACT} handler (PR 2) cancels with a clear
 * reason if the ship's state doesn't match the requested resource's required
 * extraction state.
 *
 * <p>Adding a new resource = adding an enum value + (in the seed migration)
 * rows in {@code body_resources} / {@code body_buy_prices}. No schema change.
 */
public enum ResourceKind {

    /** Solid bulk metal. Mined from rocky bodies and asteroids. */
    IRON(ShipStatus.LANDED),

    /** Frozen or liquid. Drilled from ice planets and some asteroids. */
    WATER(ShipStatus.LANDED),

    /** Atmospheric gas. Skimmed from gas giants. */
    HYDROGEN(ShipStatus.ORBITING),

    /** Atmospheric noble gas, rare and valuable. Skimmed from gas giants. */
    HELIUM(ShipStatus.ORBITING),

    /** Dense, scarce, valuable. Deep-mined from lava planets and some asteroids. */
    RARE_METAL(ShipStatus.LANDED);

    private final ShipStatus extractionState;

    ResourceKind(ShipStatus extractionState) {
        this.extractionState = extractionState;
    }

    /**
     * The ship state required to extract this resource.
     *
     * <p>Only {@link ShipStatus#LANDED} or {@link ShipStatus#ORBITING} are
     * meaningful here — {@code IDLE} / {@code MOVING} ships can't extract
     * anything by definition.
     */
    public ShipStatus extractionState() {
        return extractionState;
    }
}
