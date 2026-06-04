package org.example.springbootspacegame.order.handlers;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.body.BodyResource;
import org.example.springbootspacegame.body.BodyResourceRepository;
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
import org.example.springbootspacegame.ship.ShipType;
import org.example.springbootspacegame.ship.ShipTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * EXTRACT: pull a resource from a celestial body adjacent to the ship's tile.
 * Multi-tick; the {@code mode} param decides when it stops.
 *
 * <p>Params shape (mirrors the v3 design in issue #46):
 * <pre>{@code
 *   { "resourceKind": "IRON",
 *     "mode": "until_cancelled" | { "ticks": 50 } | { "until_full": true } }
 * }</pre>
 *
 * <p>Each tick:
 * <ol>
 *   <li>Validate that the ship is {@code ORBITING} (some body is adjacent).</li>
 *   <li>Pick the first adjacent body and find its reserve row. Absent (or zero)
 *       → cancel; complete in the "hit zero mid-extraction" case.</li>
 *   <li>Compute {@code units = min(extractRate, reserve, cargoCap - currentTotal)}.
 *       If 0 and the mode tolerates pausing ({@code until_cancelled}), stay
 *       {@code ACTIVE}; otherwise terminate per mode.</li>
 *   <li>Apply: decrement reserve, upsert {@link ShipCargo}.</li>
 *   <li>Increment the order's {@code progressTicks} counter; check mode-specific
 *       termination.</li>
 * </ol>
 *
 * <p>When a ship is adjacent to more than one body, "the first adjacent body"
 * is the deterministic pick from {@link CelestialBodyService#findFirstAdjacent}
 * (sorted by {@code (x, y, id)}). The player disambiguates by MOVE-ing to a
 * tile adjacent to only the body they want.
 *
 * <p>Param parsing tolerates either snake_case ({@code "resource_kind"}) or
 * camelCase ({@code "resourceKind"}) since the API hasn't pinned a convention
 * and Jackson defaults vary by client.
 */
@Component
@RequiredArgsConstructor
public class ExtractOrderHandler implements OrderHandler {

    private final ShipService shipService;
    private final ShipTypeRepository shipTypeRepository;
    private final CelestialBodyService celestialBodyService;
    private final BodyResourceRepository bodyResourceRepository;
    private final ShipCargoRepository shipCargoRepository;

    @Override
    public OrderKind kind() {
        return OrderKind.EXTRACT;
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        try {
            parseResourceKind(params);
            parseMode(params);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public OrderResult processOneTick(Ship ship, ShipOrder order) {
        final ResourceKind resource;
        final Mode mode;
        try {
            resource = parseResourceKind(order.getParams());
            mode = parseMode(order.getParams());
        } catch (IllegalArgumentException e) {
            return OrderResult.cancelled(e.getMessage());
        }

        ShipStatus status = shipService.positionalStatusOf(ship);
        if (status != ShipStatus.ORBITING) {
            return OrderResult.cancelled(
                    "cannot EXTRACT while " + status + " — ship must be orbiting a body");
        }

        CelestialBody body = celestialBodyService.findFirstAdjacent(ship.getX(), ship.getY()).orElse(null);
        if (body == null) {
            // ORBITING but no adjacent body — out of sync, shouldn't happen.
            return OrderResult.cancelled("no adjacent celestial body at (" + ship.getX() + ", " + ship.getY() + ")");
        }

        BodyResource reserveRow = bodyResourceRepository
                .findByBodyIdAndResourceKind(body.getId(), resource)
                .orElse(null);
        if (reserveRow == null || reserveRow.getReserve() == 0) {
            return OrderResult.cancelled(body.getName() + " has no " + resource);
        }

        ShipType type = shipTypeRepository.findById(ship.getShipTypeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ship " + ship.getId() + " references unknown shipTypeId " + ship.getShipTypeId()));
        int cargoCap = type.getCargoCapacity();
        int currentTotal = shipCargoRepository.sumQtyByShipId(ship.getId());
        int cargoFree = cargoCap - currentTotal;

        int units = Math.min(type.getExtractRate(), Math.min(reserveRow.getReserve(), cargoFree));

        if (units <= 0) {
            // Cargo is full (or reserve hit 0 but we already guarded above).
            return switch (mode) {
                // until_cancelled: stay ACTIVE-but-paused, no DB writes, wait
                // for cargo to drain (a queued SELL later in the queue runs
                // first, then this resumes).
                case Mode.UntilCancelled u -> OrderResult.inProgress();
                // ticks: cargo full mid-window terminates the order. The
                // remaining ticks are forfeit — cleaner than silently pausing.
                case Mode.Ticks t -> OrderResult.completed();
                case Mode.UntilFull u -> OrderResult.completed();
            };
        }

        reserveRow.decrementBy(units);
        upsertCargo(ship.getId(), resource, units);
        order.incrementProgressTicks();

        // Reserve hitting zero mid-extraction completes the order in every
        // mode (there's nothing left to extract).
        if (reserveRow.getReserve() == 0) {
            return OrderResult.completed();
        }

        return switch (mode) {
            case Mode.UntilCancelled u -> OrderResult.inProgress();
            case Mode.Ticks t -> order.getProgressTicks() >= t.target() ? OrderResult.completed() : OrderResult.inProgress();
            case Mode.UntilFull u -> currentTotal + units >= cargoCap ? OrderResult.completed() : OrderResult.inProgress();
        };
    }

    private void upsertCargo(java.util.UUID shipId, ResourceKind resource, int units) {
        ShipCargo existing = shipCargoRepository
                .findByShipIdAndResourceKind(shipId, resource)
                .orElse(null);
        if (existing == null) {
            shipCargoRepository.save(new ShipCargo(shipId, resource, units));
        } else {
            existing.incrementBy(units);
        }
    }

    // --- param parsing ---

    /** Mirrors the design's three duration modes. */
    private sealed interface Mode permits Mode.UntilCancelled, Mode.Ticks, Mode.UntilFull {
        record UntilCancelled() implements Mode {}
        record Ticks(int target) implements Mode {}
        record UntilFull() implements Mode {}
    }

    private static ResourceKind parseResourceKind(Map<String, Object> params) {
        Object raw = params.getOrDefault("resourceKind", params.get("resource_kind"));
        if (!(raw instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("EXTRACT params missing resourceKind");
        }
        try {
            return ResourceKind.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown resourceKind: " + s, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mode parseMode(Map<String, Object> params) {
        Object raw = params.get("mode");
        if (raw == null) {
            throw new IllegalArgumentException("EXTRACT params missing mode");
        }
        if (raw instanceof String s) {
            if ("until_cancelled".equals(s)) {
                return new Mode.UntilCancelled();
            }
            throw new IllegalArgumentException("Unknown mode string: " + s);
        }
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            if (map.containsKey("ticks")) {
                Object t = map.get("ticks");
                // Jackson produces Integer/Long for JSON integer literals, and
                // Double/BigDecimal for fractional ones. Reject fractional input
                // explicitly rather than silently truncating via Number.intValue()
                // — `{ticks: 1.9}` would otherwise become 1.
                int n;
                if (t instanceof Integer num) {
                    n = num;
                } else if (t instanceof Long num
                        && num <= Integer.MAX_VALUE && num >= Integer.MIN_VALUE) {
                    n = num.intValue();
                } else {
                    throw new IllegalArgumentException("mode.ticks must be an integer");
                }
                if (n <= 0) {
                    throw new IllegalArgumentException("mode.ticks must be > 0, was " + n);
                }
                return new Mode.Ticks(n);
            }
            if (Boolean.TRUE.equals(map.get("until_full"))) {
                return new Mode.UntilFull();
            }
        }
        throw new IllegalArgumentException("Invalid mode shape: " + raw);
    }
}
