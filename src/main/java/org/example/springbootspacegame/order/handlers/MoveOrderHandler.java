package org.example.springbootspacegame.order.handlers;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipService;
import org.example.springbootspacegame.world.WorldConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * MOVE: each tick, take one Chebyshev step toward the target tile. Completes
 * when the ship arrives. Params: {@code { "x": int, "y": int }}.
 *
 * <p>Per-tile collision rule (issue #88): the destination is rejected at
 * queue time if it's already occupied by a ship or a body, and the per-tick
 * step cancels the order if the next intermediate tile becomes occupied.
 */
@Component
@RequiredArgsConstructor
public class MoveOrderHandler implements OrderHandler {

    private final ShipService shipService;

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
        int size = WorldConstants.GRID_SIZE;
        if (xi < 0 || xi >= size || yi < 0 || yi >= size) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MOVE target must be inside the " + size + "x" + size + " grid (0 <= x,y < " + size + ")");
        }
    }

    @Override
    public void validateForShip(Ship ship, Map<String, Object> params) {
        int targetX = ((Number) params.get("x")).intValue();
        int targetY = ((Number) params.get("y")).intValue();
        // A self-target MOVE is a no-op completion at tick time — let it through.
        if (ship.getX() == targetX && ship.getY() == targetY) {
            return;
        }
        if (!shipService.isTileFree(targetX, targetY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MOVE target (" + targetX + ", " + targetY + ") is occupied");
        }
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        int targetX = intParam(order, "x");
        int targetY = intParam(order, "y");

        // Already on target — complete in one tick, no movement.
        if (ship.getX() == targetX && ship.getY() == targetY) {
            return OrderResult.completed();
        }

        // Chebyshev step: each axis moves at most 1 toward the target. Diagonals
        // count as one step. signum returns -1/0/+1.
        int dx = Integer.signum(targetX - ship.getX());
        int dy = Integer.signum(targetY - ship.getY());
        int nextX = ship.getX() + dx;
        int nextY = ship.getY() + dy;

        // Per-tile collision: if the next tile got occupied since the order was
        // queued (another ship moved into it, etc.), cancel rather than stack.
        // Replan-and-requeue is the expected player response.
        if (!shipService.isTileFree(nextX, nextY)) {
            return OrderResult.cancelled(
                    "blocked at (" + nextX + ", " + nextY + ") — replan");
        }

        ship.moveTo(nextX, nextY);

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
