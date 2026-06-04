package org.example.springbootspacegame.ship;

/**
 * Derived read-time status for a {@link Ship}. Never stored as a DB column —
 * computed in {@link ShipService} from the ship's position and order queue.
 *
 * <table>
 *   <caption>Status rules</caption>
 *   <tr><th>Status</th><th>When</th></tr>
 *   <tr><td>MOVING</td><td>Ship has at least one order with status PENDING or ACTIVE</td></tr>
 *   <tr><td>ORBITING</td><td>No active orders AND at least one celestial body sits on a tile Chebyshev-adjacent to the ship</td></tr>
 *   <tr><td>IDLE</td><td>No active orders AND no adjacent body</td></tr>
 * </table>
 *
 * <p>Ships are never on a body's own tile (issue #87): the spawn point is
 * adjacent to Earth, and bodies are not valid MOVE destinations the player
 * can park on. ORBITING is therefore purely "adjacent to ≥1 body".
 */
public enum ShipStatus {
    IDLE,
    MOVING,
    ORBITING
}
