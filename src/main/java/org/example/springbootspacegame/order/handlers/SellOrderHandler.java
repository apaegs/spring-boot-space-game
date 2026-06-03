package org.example.springbootspacegame.order.handlers;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.User;
import org.example.springbootspacegame.auth.UserRepository;
import org.example.springbootspacegame.body.BodyBuyPrice;
import org.example.springbootspacegame.body.BodyBuyPriceRepository;
import org.example.springbootspacegame.body.CelestialBody;
import org.example.springbootspacegame.body.CelestialBodyService;
import org.example.springbootspacegame.order.OrderHandler;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderResult;
import org.example.springbootspacegame.order.ShipOrder;
import org.example.springbootspacegame.resource.ResourceKind;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipCargo;
import org.example.springbootspacegame.ship.ShipCargoRepository;
import org.example.springbootspacegame.ship.ShipService;
import org.example.springbootspacegame.ship.ShipStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * SELL: convert all cargo of a given resource to credits at the celestial body
 * the ship is on. One tick, params: {@code { "resourceKind": "IRON" }}.
 *
 * <p>Cancels if the ship isn't at a body (status != LANDED/ORBITING), the body
 * doesn't buy this resource (no row in {@code body_buy_prices}), or the ship
 * has no cargo of this kind.
 *
 * <p>Successful sale: {@code credits += qty * pricePerUnit}, then the
 * {@code ship_cargo} row is deleted (the entity invariant forbids qty = 0
 * rows — empty cargo is represented by no row).
 */
@Component
@RequiredArgsConstructor
public class SellOrderHandler implements OrderHandler {

    private final ShipService shipService;
    private final UserRepository userRepository;
    private final CelestialBodyService celestialBodyService;
    private final BodyBuyPriceRepository bodyBuyPriceRepository;
    private final ShipCargoRepository shipCargoRepository;

    @Override
    public OrderKind kind() {
        return OrderKind.SELL;
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        try {
            parseResourceKind(params);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        final ResourceKind resource;
        try {
            resource = parseResourceKind(order.getParams());
        } catch (IllegalArgumentException e) {
            return OrderResult.cancelled(e.getMessage());
        }

        ShipStatus status = shipService.positionalStatusOf(ship);
        if (status != ShipStatus.LANDED && status != ShipStatus.ORBITING) {
            return OrderResult.cancelled("cannot SELL while " + status + " — ship must be at a body");
        }

        CelestialBody body = celestialBodyService.findAt(ship.getX(), ship.getY()).orElse(null);
        if (body == null) {
            return OrderResult.cancelled("no celestial body at (" + ship.getX() + ", " + ship.getY() + ")");
        }

        BodyBuyPrice price = bodyBuyPriceRepository
                .findByBodyIdAndResourceKind(body.getId(), resource)
                .orElse(null);
        if (price == null) {
            return OrderResult.cancelled(body.getName() + " does not buy " + resource);
        }

        ShipCargo cargo = shipCargoRepository
                .findByShipIdAndResourceKind(ship.getId(), resource)
                .orElse(null);
        if (cargo == null) {
            // No cargo row of this kind. The entity invariant means there's
            // also no zero-qty row hanging around — nothing to sell.
            return OrderResult.cancelled("no " + resource + " in cargo to sell");
        }

        // qty * pricePerUnit fits comfortably in long (Integer.MAX * Integer.MAX
        // is still under Long.MAX), so the multiplication can't overflow. The
        // accumulation into credits is guarded by User.addCredits' Math.addExact.
        long earned = (long) cargo.getQty() * price.getPricePerUnit();

        User user = userRepository.findById(ship.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ship " + ship.getId() + " references unknown user " + ship.getUserId()));
        user.addCredits(earned);

        shipCargoRepository.delete(cargo);

        return OrderResult.completed();
    }

    private static ResourceKind parseResourceKind(Map<String, Object> params) {
        Object raw = params.getOrDefault("resourceKind", params.get("resource_kind"));
        if (!(raw instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("SELL params missing resourceKind");
        }
        try {
            return ResourceKind.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown resourceKind: " + s, e);
        }
    }
}
