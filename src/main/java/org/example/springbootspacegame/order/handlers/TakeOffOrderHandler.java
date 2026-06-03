package org.example.springbootspacegame.order.handlers;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipService;
import org.example.springbootspacegame.ship.ShipStatus;
import org.springframework.stereotype.Component;

/**
 * TAKE_OFF: lift off from a celestial body. One tick, no params.
 *
 * <p>Counterpart to {@code LAND}. Cancels if the ship isn't currently at a
 * body — taking off from {@code IDLE} or {@code MOVING} is meaningless.
 * After completion, {@link ShipService#statusOf} returns {@link ShipStatus#IDLE}
 * (the most recent of LAND/TAKE_OFF is now TAKE_OFF).
 *
 * <p>Usually auto-prepended before a {@code MOVE} order when the ship is at
 * a body — the player rarely queues TAKE_OFF manually.
 */
@Component
@RequiredArgsConstructor
public class TakeOffOrderHandler implements OrderHandler {

    private final ShipService shipService;

    @Override
    public OrderKind kind() {
        return OrderKind.TAKE_OFF;
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        ShipStatus status = shipService.positionalStatusOf(ship);
        if (status != ShipStatus.LANDED && status != ShipStatus.ORBITING) {
            return OrderResult.cancelled(
                    "cannot take off — ship is " + status + ", not at a body");
        }
        return OrderResult.completed();
    }
}
