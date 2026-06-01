package org.example.springbootspacegame.planet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanetRepository extends JpaRepository<Planet, UUID> {

    /**
     * "Is there a planet on this tile?" — used by the LAND order handler to
     * validate that a ship is actually on a planet before completing the order.
     * Hits the {@code planets_xy_unique} index, single seek.
     */
    Optional<Planet> findByXAndY(int x, int y);

    /**
     * Cheaper than {@link #findByXAndY} when the caller only needs to know
     * whether a planet exists at the given coordinates — used by
     * {@link org.example.springbootspacegame.ship.ShipService} to derive
     * {@link org.example.springbootspacegame.ship.ShipStatus#LANDED}.
     */
    boolean existsByXAndY(int x, int y);
}
