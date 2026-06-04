package org.example.springbootspacegame.body;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CelestialBodyRepository extends JpaRepository<CelestialBody, UUID> {

    /**
     * Bodies whose tile falls inside the Chebyshev-distance-1 box around
     * {@code (x, y)} — i.e. the 9 tiles {@code [x-1..x+1] × [y-1..y+1]}.
     * Used by status derivation (ship is ORBITING when at least one body is
     * adjacent) and by the EXTRACT / SELL handlers to pick the body the ship
     * is currently orbiting. Bounds-tolerant: passing negative or oversized
     * coordinates just returns no rows.
     *
     * <p>Ordered by {@code (x, y, id)} so callers picking "the" adjacent body
     * get a stable choice when multiple bodies surround the ship.
     */
    List<CelestialBody> findByXBetweenAndYBetweenOrderByXAscYAscIdAsc(
            int minX, int maxX, int minY, int maxY);

    /**
     * "Is there a body on this tile?" — backs the per-tile collision rule
     * (issue #88). Bodies are stationary so this is an immovable obstacle for
     * MOVE destinations and spawn candidates. The {@code celestial_bodies_xy_unique}
     * index makes it a single index seek.
     */
    boolean existsByXAndY(int x, int y);
}
