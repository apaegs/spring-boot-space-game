package org.example.springbootspacegame.ship;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.User;
import org.example.springbootspacegame.auth.UserRepository;
import org.example.springbootspacegame.body.CelestialBodyKind;
import org.example.springbootspacegame.body.CelestialBodyService;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.OrderStatus;
import org.example.springbootspacegame.order.ShipOrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipService {

    // Center of the grid. Hard-coded for v1 — deterministic for tests and a
    // single source of truth for "where new players appear". Grid bounds live
    // in WorldConstants; spawn point is a separate game-design choice, not
    // derived from grid size.
    static final int SPAWN_X = 50;
    static final int SPAWN_Y = 50;

    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.ACTIVE);

    private final ShipRepository shipRepository;
    private final UserRepository userRepository;
    private final ShipOrderRepository shipOrderRepository;
    private final CelestialBodyService celestialBodyService;

    /**
     * Create the auto-mothership for a brand-new user. Called from
     * {@code AuthService.register} inside the same transaction as the user
     * insert. No concurrency concern — only the registration flow can reach
     * this user before commit.
     */
    @Transactional
    public Ship createForNewUser(UUID userId, String username) {
        Ship ship = new Ship(userId, autoName(username, 0), SPAWN_X, SPAWN_Y, ShipType.MOTHERSHIP_ID);
        return shipRepository.save(ship);
    }

    /**
     * Create an additional ship for an existing player ({@code POST /api/ships}).
     *
     * <p>Race control: takes a {@code SELECT ... FOR UPDATE} on the caller's
     * user row, which serializes all concurrent ship-create transactions for
     * the same user. With the lock held, {@link ShipRepository#countByUserId}
     * is safe to base the next auto-name on. The unique constraint on
     * {@code (user_id, name)} in V6 is defence-in-depth for any case the lock
     * misses or a player-supplied custom name collides.
     */
    @Transactional
    public ShipDto createShipForCurrentUser(UUID userId, CreateShipRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        String desired = request != null ? request.name() : null;
        String name = desired != null
                ? desired
                : autoName(user.getUsername(), shipRepository.countByUserId(userId));

        Ship ship = new Ship(userId, name, SPAWN_X, SPAWN_Y, ShipType.MOTHERSHIP_ID);
        try {
            Ship saved = shipRepository.saveAndFlush(ship);
            return ShipDto.from(saved, deriveStatus(saved));
        } catch (DataIntegrityViolationException e) {
            // Only reachable if a player-supplied custom name collides with an
            // existing ship's name. Auto-named conflicts can't reach here
            // because of the per-user lock above.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A ship named '" + name + "' already exists", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ShipDto> listForUser(UUID userId) {
        return shipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ship -> ShipDto.from(ship, deriveStatus(ship)))
                .toList();
    }

    /**
     * Public projection of every ship in the world — used by the map to render
     * other players' ships alongside the caller's own. Returns the narrow
     * {@link PublicShipDto} (no {@code userId}, no {@code createdAt}); foreign
     * ships are visible but anonymous per the design in issue #35.
     *
     * <p>The caller's own ships are included in the result. The frontend dedupes
     * against the private fleet list it already holds, so the only extra cost
     * over a "foreign-only" variant is a handful of bytes per ship.
     */
    @Transactional(readOnly = true)
    public List<PublicShipDto> listAllPublic() {
        return shipRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(PublicShipDto::from)
                .toList();
    }

    /**
     * Ownership-checked lookup used by ship-scoped endpoints (orders etc.).
     * Throws 404 if the ship doesn't exist or belongs to someone else —
     * deliberately indistinguishable from the outside so we don't leak the
     * existence of other users' ships.
     */
    @Transactional
    public ShipDto renameShip(UUID userId, UUID shipId, String name) {
        Ship ship = shipRepository.findByIdAndUserId(shipId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ship not found"));
        ship.rename(name);
        try {
            Ship saved = shipRepository.saveAndFlush(ship);
            return ShipDto.from(saved, deriveStatus(saved));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A ship named '" + name + "' already exists", e);
        }
    }

    @Transactional(readOnly = true)
    public Ship requireOwnedShip(UUID userId, UUID shipId) {
        return shipRepository.findByIdAndUserId(shipId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ship not found"));
    }

    /**
     * Read-time status as the UI sees it. Wraps {@link #deriveStatus(Ship)} so
     * external callers (frontend, auth, etc.) don't need to bypass the
     * private-ness of the original method. Includes the {@code MOVING}
     * short-circuit, so an order handler asking about its OWN ship's status
     * while it's the active order will see {@code MOVING} — use
     * {@link #positionalStatusOf} from inside a handler instead.
     */
    public ShipStatus statusOf(Ship ship) {
        return deriveStatus(ship);
    }

    /**
     * "Where is the ship sitting?" — LANDED / ORBITING / IDLE only, ignoring
     * whatever ACTIVE order is in flight. Used by order handlers
     * (TAKE_OFF / EXTRACT / SELL) and the auto-prerequisite middleware to ask
     * the only question they actually care about: <i>is the ship at a body</i>.
     *
     * <p>Without this, a handler calling {@link #statusOf} from inside its own
     * {@code processOneTick} would see its own ACTIVE order as a queued
     * activity and incorrectly classify the ship as {@code MOVING}.
     */
    public ShipStatus positionalStatusOf(Ship ship) {
        return shipOrderRepository
                .findFirstByShipIdAndStatusAndKindInOrderByCompletedAtDesc(
                        ship.getId(), OrderStatus.COMPLETED,
                        java.util.List.of(OrderKind.LAND, OrderKind.TAKE_OFF))
                .map(o -> {
                    if (o.getKind() == OrderKind.TAKE_OFF) {
                        return ShipStatus.IDLE;
                    }
                    return celestialBodyService.findAt(ship.getX(), ship.getY())
                            .map(body -> body.getKind() == CelestialBodyKind.GAS_GIANT
                                    ? ShipStatus.ORBITING
                                    : ShipStatus.LANDED)
                            .orElse(ShipStatus.IDLE);
                })
                .orElse(ShipStatus.IDLE);
    }

    /**
     * Derives the read-time {@link ShipStatus} for a ship without storing it.
     *
     * <ul>
     *   <li>{@code MOVING}   — ship has at least one PENDING or ACTIVE order</li>
     *   <li>{@code LANDED}   — last completed LAND/TAKE_OFF was LAND, and the ship is currently on a non-gas-giant body</li>
     *   <li>{@code ORBITING} — last completed LAND/TAKE_OFF was LAND, and the body it's on is a {@code GAS_GIANT}</li>
     *   <li>{@code IDLE}     — anything else (no LAND ever, last was TAKE_OFF, or LAND but ship has since moved off the body)</li>
     * </ul>
     *
     * <p>The "LANDED vs ORBITING" choice is read-time, not stored — it's a
     * pure function of the body's {@code kind} at the ship's current position.
     * Means a body's kind change would retroactively re-classify the ship's
     * state (impossible in v1, but the model handles it).
     *
     * <p>LAND is also intentionally order-history-based, not coordinate-based.
     * A ship that drifts onto a body's tile without LAND-ing is still IDLE —
     * matching the domain rule that arrival is an explicit action.
     *
     * <p>Must be called within an active transaction so the repository calls
     * participate in the same snapshot.
     */
    private ShipStatus deriveStatus(Ship ship) {
        if (shipOrderRepository.existsByShipIdAndStatusIn(ship.getId(), ACTIVE_STATUSES)) {
            return ShipStatus.MOVING;
        }
        return positionalStatusOf(ship);
    }

    private static String autoName(String username, long existingCount) {
        // First ship: "<username>'s ship" (matches the historic single-ship name).
        // Subsequent ships: "<username>'s ship 2", "<username>'s ship 3", ...
        if (existingCount == 0) {
            return username + "'s ship";
        }
        return username + "'s ship " + (existingCount + 1);
    }
}
