package org.example.springbootspacegame.order.handlers;

import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.ship.Ship;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

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
    public void validateParams(Map<String, Object> params) {
        Object x = params.get("x");
        Object y = params.get("y");
        if (!(x instanceof Number) || !(y instanceof Number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MOVE requires numeric params 'x' and 'y'");
        }
        int xi = ((Number) x).intValue();
        int yi = ((Number) y).intValue();
        if (xi < 0 || xi >= 100 || yi < 0 || yi >= 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MOVE target must be inside the 100x100 grid (0 <= x,y < 100)");
        }
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
