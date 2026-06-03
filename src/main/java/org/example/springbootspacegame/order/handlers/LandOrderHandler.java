package org.example.springbootspacegame.order.handlers;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.body.CelestialBodyService;
import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.ship.Ship;
import org.springframework.stereotype.Component;

/**
 * LAND: completes if the ship is on a celestial body's tile, cancels otherwise.
 * No params.
 *
 * <p>Always resolves in one tick — landing isn't a process, it's an
 * instantaneous "open the body UI when you arrive" beat. The player typically
 * chains it after a MOVE in the same queue.
 *
 * <p>v1 (PR 1): always treats arrival as {@code LANDED} regardless of body
 * kind. PR 2 makes the resolution context-aware so {@code GAS_GIANT} resolves
 * to {@code ORBITING}.
 */
@Component
@RequiredArgsConstructor
public class LandOrderHandler implements OrderHandler {

    private final CelestialBodyService celestialBodyService;

    @Override
    public OrderKind kind() {
        return OrderKind.LAND;
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        return celestialBodyService.findAt(ship.getX(), ship.getY())
                .<OrderResult>map(body -> OrderResult.completed())
                .orElseGet(() -> OrderResult.cancelled(
                        "no celestial body at (" + ship.getX() + ", " + ship.getY() + ")"));
    }
}
