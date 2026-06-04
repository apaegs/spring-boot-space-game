package org.example.springbootspacegame.ship;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.User;
import org.example.springbootspacegame.auth.UserRepository;
import org.example.springbootspacegame.body.CelestialBodyRepository;
import org.example.springbootspacegame.body.CelestialBodyService;
import org.example.springbootspacegame.order.OrderStatus;
import org.example.springbootspacegame.order.ShipOrderRepository;
import org.example.springbootspacegame.world.WorldConstants;
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

    // Preferred origin tile — one tile east of Earth (seeded at 50,50 in V9).
    // Adjacent to Earth so a fresh player's mothership derives ORBITING
    // immediately. When occupied (multi-player race, prior test ships,
    // returning user with multiple ships) the spawn search spirals outward
    // from here.
    static final int SPAWN_X = 51;
    static final int SPAWN_Y = 50;

    // How far the spawn-spiral walks before giving up. Radius 10 = 441
    // candidate tiles around Earth — comfortable headroom for any realistic
    // player count, but bounded so a degenerate "everything full" state
    // returns 503 instead of degenerating to a far-corner spawn the player
    // wouldn't expect.
    private static final int MAX_SPAWN_RADIUS = 10;

    // Retries for the cross-transaction spawn race that the per-user lock
    // doesn't cover (two registrations both reading "(51,50) is free" then
    // both inserting). The UNIQUE(x,y) constraint catches the loser at
    // insert; this many retries of the spiral handles it gracefully.
    private static final int SPAWN_RETRY_ATTEMPTS = 3;

    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.ACTIVE);

    private final ShipRepository shipRepository;
    private final UserRepository userRepository;
    private final ShipOrderRepository shipOrderRepository;
    private final CelestialBodyService celestialBodyService;
    private final CelestialBodyRepository celestialBodyRepository;
    private final ShipTypeRepository shipTypeRepository;
    private final ShipCargoRepository shipCargoRepository;

    /**
     * Create the auto-mothership for a brand-new user. Called from
     * {@code AuthService.register} inside the same transaction as the user
     * insert. The {@code AuthService}-level user uniqueness check prevents
     * two registrations from racing on the same user; the spawn-spiral plus
     * {@code UNIQUE(x, y)} on {@code ships} (V12) handles the cross-user race
     * where two new registrations both want (51, 50).
     */
    @Transactional
    public Ship createForNewUser(UUID userId, String username) {
        return spawnShip(userId, autoName(username, 0));
    }

    /**
     * Create an additional ship for an existing player ({@code POST /api/ships}).
     *
     * <p>Race control: takes a {@code SELECT ... FOR UPDATE} on the caller's
     * user row, which serializes all concurrent ship-create transactions for
     * the same user. With the lock held, {@link ShipRepository#countByUserId}
     * is safe to base the next auto-name on. The unique constraint on
     * {@code (user_id, name)} in V6 is defence-in-depth for any case the lock
     * misses or a player-supplied custom name collides. The spawn-tile
     * collision case is handled by the same spiral as new-user spawn.
     */
    @Transactional
    public ShipDto createShipForCurrentUser(UUID userId, CreateShipRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        String desired = request != null ? request.name() : null;
        String name = desired != null
                ? desired
                : autoName(user.getUsername(), shipRepository.countByUserId(userId));

        try {
            Ship saved = spawnShip(userId, name);
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            // (user_id, name) collision on a player-supplied custom name.
            // Auto-named conflicts can't reach here because of the per-user
            // lock above. The xy-unique race is handled inside spawnShip.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A ship named '" + name + "' already exists", e);
        }
    }

    /**
     * Save a new {@link Ship} at the first free tile near {@link #SPAWN_X},
     * {@link #SPAWN_Y}, retrying the spiral on a cross-transaction race that
     * loses to the {@code ships_xy_unique} constraint. Throws 503 if no
     * candidate tile is free after {@link #MAX_SPAWN_RADIUS}, or if all
     * {@link #SPAWN_RETRY_ATTEMPTS} retries lose the race.
     */
    private Ship spawnShip(UUID userId, String name) {
        DataIntegrityViolationException lastRace = null;
        for (int attempt = 0; attempt < SPAWN_RETRY_ATTEMPTS; attempt++) {
            int[] tile = findFreeSpawnTile();
            Ship ship = new Ship(userId, name, tile[0], tile[1], ShipType.MOTHERSHIP_ID);
            try {
                return shipRepository.saveAndFlush(ship);
            } catch (DataIntegrityViolationException e) {
                // Distinguish the xy-unique race (retryable) from the
                // (user_id, name) collision (not retryable — rethrow so the
                // caller can map it to a 409).
                if (!isXyUniqueViolation(e)) {
                    throw e;
                }
                lastRace = e;
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not find a free spawn tile after " + SPAWN_RETRY_ATTEMPTS + " attempts", lastRace);
    }

    private static boolean isXyUniqueViolation(DataIntegrityViolationException e) {
        // The constraint name is stable (set in V12). Match on it rather than
        // the root cause class so we're robust to driver/dialect changes.
        String message = e.getMessage();
        return message != null && message.contains("ships_xy_unique");
    }

    /**
     * First free tile in expanding Chebyshev rings around the preferred
     * spawn. Rings are walked in {@code (dy, dx)} order so the per-ring
     * walk is deterministic — tests can predict which tile the Nth contested
     * spawn will pick. Returns {@code [x, y]}; throws 503 past
     * {@link #MAX_SPAWN_RADIUS}.
     */
    private int[] findFreeSpawnTile() {
        int size = WorldConstants.GRID_SIZE;
        for (int r = 0; r <= MAX_SPAWN_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    // Only the ring at exactly Chebyshev distance r — skips
                    // tiles already covered in earlier rings.
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = SPAWN_X + dx;
                    int y = SPAWN_Y + dy;
                    if (x < 0 || x >= size || y < 0 || y >= size) continue;
                    if (isTileFree(x, y)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No free spawn tile within Chebyshev radius " + MAX_SPAWN_RADIUS + " of (" + SPAWN_X + ", " + SPAWN_Y + ")");
    }

    @Transactional(readOnly = true)
    public List<ShipDto> listForUser(UUID userId) {
        return shipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toDto)
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
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A ship named '" + name + "' already exists", e);
        }
    }

    /**
     * Build a {@link ShipDto} for a single ship, joining in ship type and
     * cargo. Centralized here so every callsite gets the same shape — and
     * adding fields to the DTO only requires updating one place.
     *
     * <p>One DB lookup for the ship type, one for the cargo. For multi-ship
     * users this is N+1; v1 has 1 ship per user so the saving from batching
     * isn't yet worth the complexity. Revisit if a future "fleet view"
     * surfaces a real-user player with many ships.
     */
    private ShipDto toDto(Ship ship) {
        ShipType type = shipTypeRepository.findById(ship.getShipTypeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ship " + ship.getId() + " references unknown shipTypeId " + ship.getShipTypeId()));
        List<ShipCargo> cargo = shipCargoRepository.findByShipId(ship.getId());
        return ShipDto.from(ship, deriveStatus(ship), type, cargo);
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
     * "Where is the ship sitting?" — {@code ORBITING} if at least one
     * celestial body is on a Chebyshev-adjacent tile, {@code IDLE} otherwise.
     * Ignores any in-flight order so a handler can call this on its own ship
     * mid-tick without seeing itself as {@code MOVING}.
     */
    public ShipStatus positionalStatusOf(Ship ship) {
        return celestialBodyService.findFirstAdjacent(ship.getX(), ship.getY())
                .map(b -> ShipStatus.ORBITING)
                .orElse(ShipStatus.IDLE);
    }

    /**
     * Per-tile collision check (issue #88): is the tile at {@code (x, y)}
     * free of both ships and celestial bodies? Bodies are stationary
     * obstacles; ships are moving ones. Used by the MOVE handler at queue
     * time and per-tick, and by the spawn-spiral.
     *
     * <p>Two index seeks. No special handling for out-of-bounds — callers
     * (MOVE handler, spawn-spiral) already filter coordinates against
     * {@link WorldConstants#GRID_SIZE}.
     */
    @Transactional(readOnly = true)
    public boolean isTileFree(int x, int y) {
        return !shipRepository.existsByXAndY(x, y)
                && !celestialBodyRepository.existsByXAndY(x, y);
    }

    /**
     * Derives the read-time {@link ShipStatus} for a ship without storing it.
     *
     * <ul>
     *   <li>{@code MOVING}   — ship has at least one PENDING or ACTIVE order</li>
     *   <li>{@code ORBITING} — no active orders AND at least one celestial body is on a Chebyshev-adjacent tile</li>
     *   <li>{@code IDLE}     — no active orders AND no adjacent body</li>
     * </ul>
     *
     * <p>Position-derived: a ship that drifts adjacent to a body is ORBITING
     * without any explicit order — matches the orbit-only model from #87.
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
