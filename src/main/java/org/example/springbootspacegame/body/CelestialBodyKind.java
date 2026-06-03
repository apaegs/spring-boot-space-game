package org.example.springbootspacegame.body;

/**
 * Taxonomy of celestial bodies. The seed (V9) places ~40 bodies of mixed kinds
 * across the 100×100 grid; the design body of issue #46 (v3 comment) lays out
 * the yield profile per kind.
 *
 * <p>The {@code LAND} order handler (updated in PR 2) reads the kind to decide
 * which {@code ShipStatus} the ship arrives in — most kinds resolve to
 * {@code LANDED}, but {@link #GAS_GIANT} resolves to {@code ORBITING} since
 * you can't physically land on a gas giant.
 */
public enum CelestialBodyKind {

    /** Common. Iron-rich, trace water and rare metal. Land. */
    ROCKY_PLANET,

    /** Rare. Heavy rare-metal yield; some iron. Land. */
    LAVA_PLANET,

    /** Water-heavy; occasional hydrogen seep. Land. */
    ICE_PLANET,

    /**
     * Hydrogen + helium atmosphere. {@code LAND} resolves to {@code ORBITING}
     * here — you can't physically land on a gas giant.
     */
    GAS_GIANT,

    /** Small, many on the map. Mixed iron/water/rare-metal trace. Land. */
    ASTEROID,

    /**
     * Decorative + navigation landmark in v1. No resources to extract; no
     * arrival action.
     */
    STAR
}
