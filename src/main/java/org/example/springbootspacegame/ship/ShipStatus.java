package org.example.springbootspacegame.ship;

/**
 * Derived read-time status for a {@link Ship}. Never stored as a DB column —
 * computed in {@link ShipService} by inspecting the order queue and the ship's
 * current position.
 *
 * <table>
 *   <caption>Status rules</caption>
 *   <tr><th>Status</th><th>When</th></tr>
 *   <tr><td>MOVING</td><td>Ship has at least one order with status PENDING or ACTIVE</td></tr>
 *   <tr><td>LANDED</td><td>No pending/active orders AND ship is on a planet tile (x,y matches a planet)</td></tr>
 *   <tr><td>IDLE</td><td>No pending/active orders AND ship is NOT on a planet tile</td></tr>
 * </table>
 */
public enum ShipStatus {
    IDLE,
    MOVING,
    LANDED
}
