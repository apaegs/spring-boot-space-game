package org.example.springbootspacegame.ship;

import org.example.springbootspacegame.resource.ResourceKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipCargoRepository extends JpaRepository<ShipCargo, ShipCargoId> {

    /**
     * All cargo rows for a ship — both for the ship-info panel (PR 3) and for
     * the EXTRACT handler (PR 2) to compute the current total against
     * {@code cargo_capacity}.
     */
    List<ShipCargo> findByShipId(UUID shipId);

    /**
     * Cargo of a specific resource. Used by SELL (PR 2) to compute the
     * credit total before deleting the row.
     */
    Optional<ShipCargo> findByShipIdAndResourceKind(UUID shipId, ResourceKind resourceKind);

    /**
     * Total units across all resources for a ship. Used by EXTRACT (PR 2) to
     * compare against {@code cargo_capacity}. Returns 0 if the ship has no
     * cargo rows yet.
     */
    @Query("SELECT COALESCE(SUM(c.qty), 0) FROM ShipCargo c WHERE c.shipId = :shipId")
    int sumQtyByShipId(@Param("shipId") UUID shipId);
}
