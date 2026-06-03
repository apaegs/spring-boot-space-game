package org.example.springbootspacegame.body;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CelestialBodyRepository extends JpaRepository<CelestialBody, UUID> {

    /**
     * "Is there a body on this tile?" — used by the LAND order handler to
     * validate that a ship is actually on a body before completing the order.
     * Hits the {@code celestial_bodies_xy_unique} index, single seek.
     */
    Optional<CelestialBody> findByXAndY(int x, int y);

    /**
     * Cheaper than {@link #findByXAndY} when the caller only needs to know
     * whether a body exists at the given coordinates.
     */
    boolean existsByXAndY(int x, int y);
}
