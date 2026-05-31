package org.example.springbootspacegame.order.handlers;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.planet.PlanetService;
import org.example.springbootspacegame.ship.Ship;
import org.springframework.stereotype.Component;

/**
 * LAND: completes if the ship is on a planet tile, cancels otherwise.
 * No params.
 *
 * <p>Always resolves in one tick — landing isn't a process, it's an
 * instantaneous "open the planet UI when you arrive" beat. The player
 * typically chains it after a MOVE in the same queue.
 */
@Component
@RequiredArgsConstructor
public class LandOrderHandler implements OrderHandler {

    private final PlanetService planetService;

    @Override
    public OrderKind kind() {
        return OrderKind.LAND;
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        return planetService.findAt(ship.getX(), ship.getY())
                .<OrderResult>map(planet -> OrderResult.completed())
                .orElseGet(() -> OrderResult.cancelled(
                        "no planet at (" + ship.getX() + ", " + ship.getY() + ")"));
    }
}
