package org.example.springbootspacegame.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipOrderRepository extends JpaRepository<ShipOrder, UUID> {

    /**
     * Player-visible queue for a ship: pending + currently-processing orders,
     * oldest first. Completed/cancelled orders are filtered out (use a separate
     * history query when we add that).
     */
    List<ShipOrder> findByShipIdAndStatusInOrderByCreatedAtAsc(UUID shipId, List<OrderStatus> statuses);

    /**
     * The next order the tick processor should advance for a given ship. The
     * partial index {@code ship_orders_active_idx} (see V4) makes this a single
     * index seek. Returns empty if the ship has nothing queued.
     */
    Optional<ShipOrder> findFirstByShipIdAndStatusInOrderByCreatedAtAsc(UUID shipId, List<OrderStatus> statuses);

    /**
     * Ownership-checked lookup — used by the cancel endpoint so a player can
     * only cancel orders on their own ship.
     */
    Optional<ShipOrder> findByIdAndShipId(UUID id, UUID shipId);

    /**
     * Distinct ship IDs that have at least one order in any of the given statuses.
     * Used by the tick processor to scope "which ships need processing this tick"
     * to just the ones with queued work — most ships idle most of the time.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT o.shipId FROM ShipOrder o WHERE o.status IN :statuses")
    List<UUID> findDistinctShipIdsByStatusIn(@org.springframework.data.repository.query.Param("statuses") List<OrderStatus> statuses);
}
