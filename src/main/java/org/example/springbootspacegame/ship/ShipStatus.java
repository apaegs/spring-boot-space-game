package org.example.springbootspacegame.ship;

/**
 * Derived read-time status for a {@link Ship}. Never stored as a DB column —
 * computed in {@link ShipService} by inspecting the order queue.
 *
 * <table>
 *   <caption>Status rules</caption>
 *   <tr><th>Status</th><th>When</th></tr>
 *   <tr><td>MOVING</td><td>Ship has at least one order with status PENDING or ACTIVE</td></tr>
 *   <tr><td>LANDED</td><td>No active orders AND the ship's last completed order was {@code LAND} on a body that resolves to LANDED</td></tr>
 *   <tr><td>ORBITING</td><td>No active orders AND the ship's last completed order was {@code LAND} on a body that resolves to ORBITING (e.g. {@code GAS_GIANT})</td></tr>
 *   <tr><td>IDLE</td><td>None of the above</td></tr>
 * </table>
 *
 * <p>{@code ORBITING} is declared here in PR 1 so that {@code ResourceKind.extractionState()}
 * can reference it, but the derivation that actually returns {@code ORBITING}
 * lands in PR 2 alongside the context-aware {@code LandOrderHandler}.
 */
public enum ShipStatus {
    IDLE,
    MOVING,
    LANDED,
    ORBITING
}
