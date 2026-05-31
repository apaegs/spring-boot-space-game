package org.example.springbootspacegame.order.handlers;

import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.ship.Ship;
import org.springframework.stereotype.Component;

/**
 * MOVE: each tick, take one Chebyshev step toward the target tile. Completes
 * when the ship arrives. Params: {@code { "x": int, "y": int }}.
 */
@Component
public class MoveOrderHandler implements OrderHandler {

    @Override
    public OrderKind kind() {
        return OrderKind.MOVE;
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        int targetX = intParam(order, "x");
        int targetY = intParam(order, "y");

        // Already on target (e.g. queued LAND while on a planet, then a MOVE
        // to that same tile redundantly). Complete in one tick, no movement.
        if (ship.getX() == targetX && ship.getY() == targetY) {
            return OrderResult.completed();
        }

        // Chebyshev step: each axis moves at most 1 toward the target. Diagonals
        // count as one step. signum returns -1/0/+1.
        int dx = Integer.signum(targetX - ship.getX());
        int dy = Integer.signum(targetY - ship.getY());
        ship.moveTo(ship.getX() + dx, ship.getY() + dy);

        // Arrived this tick? Then complete; otherwise stay in progress for next tick.
        if (ship.getX() == targetX && ship.getY() == targetY) {
            return OrderResult.completed();
        }
        return OrderResult.inProgress();
    }

    private static int intParam(ShipOrder order, String name) {
        Object raw = order.getParams().get(name);
        if (!(raw instanceof Number n)) {
            throw new IllegalStateException(
                    "MOVE order " + order.getId() + " is missing numeric param '" + name + "'");
        }
        return n.intValue();
    }
}
