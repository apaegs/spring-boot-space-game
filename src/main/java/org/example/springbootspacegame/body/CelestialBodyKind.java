package org.example.springbootspacegame.body;

/**
 * Taxonomy of celestial bodies. The seed (V9) places ~40 bodies of mixed kinds
 * across the 100×100 grid; the design body of issue #46 (v3 comment) lays out
 * the yield profile per kind.
 *
 * <p>Kind is descriptive only — it drives the seeded resource/buy-price matrix
 * but no longer changes ship-state derivation. Per the orbit-only model (#87)
 * any ship Chebyshev-adjacent to any kind of body is {@code ORBITING}; the
 * "you can't land on a gas giant" intuition is now expressed by ships never
 * sitting on a body's tile at all.
 */
public enum CelestialBodyKind {

    /** Common. Iron-rich, trace water and rare metal. */
    ROCKY_PLANET,

    /** Rare. Heavy rare-metal yield; some iron. */
    LAVA_PLANET,

    /** Water-heavy; occasional hydrogen seep. */
    ICE_PLANET,

    /** Hydrogen + helium atmosphere. */
    GAS_GIANT,

    /** Small, many on the map. Mixed iron/water/rare-metal trace. */
    ASTEROID,

    /** Decorative + navigation landmark in v1. No resources to extract. */
    STAR
}
