package org.example.springbootspacegame.ship;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.User;
import org.example.springbootspacegame.auth.UserRepository;
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
     * Derives the read-time {@link ShipStatus} for a ship without storing it.
     *
     * <ul>
     *   <li>MOVING — ship has at least one PENDING or ACTIVE order</li>
     *   <li>LANDED — no active orders AND the ship's last completed order was LAND</li>
     *   <li>IDLE   — anything else</li>
     * </ul>
     *
     * <p>LANDED is intentionally order-history-based, not coordinate-based.
     * A ship that simply stops on a body tile without issuing a LAND order
     * is IDLE — matching the domain rule that landing is an explicit action.
     *
     * <p>Must be called within an active transaction so the repository calls
     * participate in the same snapshot.
     */
    private ShipStatus deriveStatus(Ship ship) {
        if (shipOrderRepository.existsByShipIdAndStatusIn(ship.getId(), ACTIVE_STATUSES)) {
            return ShipStatus.MOVING;
        }
        return shipOrderRepository
                .findFirstByShipIdAndStatusOrderByCompletedAtDesc(ship.getId(), OrderStatus.COMPLETED)
                .filter(o -> o.getKind() == OrderKind.LAND)
                .map(o -> ShipStatus.LANDED)
                .orElse(ShipStatus.IDLE);
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
