package org.example.springbootspacegame.order;

/**
 * Lifecycle states for a {@link ShipOrder}. Stored as VARCHAR; the DB CHECK
 * constraint mirrors this enum.
 *
 * <p>Transitions:
 * <pre>
 *   PENDING --(picked up by processor)--> ACTIVE --(processing finished)--> COMPLETED
 *                                                                      \-> CANCELLED
 *   PENDING --(player cancelled)----------------------------------------> CANCELLED
 * </pre>
 *
 * <p>Once an order is COMPLETED or CANCELLED it's never re-processed.
 * The partial index in V4 only includes PENDING and ACTIVE rows.
 */
public enum OrderStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
