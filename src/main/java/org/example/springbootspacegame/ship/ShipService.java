package org.example.springbootspacegame.ship;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipService {

    // Center of the 100x100 grid. Hard-coded for v1 — deterministic for tests and
    // a single source of truth for "where new players appear". See #29 (single
    // source of truth for grid size) for the longer-term decision.
    static final int SPAWN_X = 50;
    static final int SPAWN_Y = 50;

    private final ShipRepository shipRepository;

    /**
     * Create a new ship for the user. Called from two places:
     *
     * <ul>
     *   <li>{@code AuthService.register} — auto-create the player's first ship
     *       inside the same transaction so a new user always has a mothership.</li>
     *   <li>{@code POST /api/ships} — when the player explicitly creates an
     *       additional ship via the API.</li>
     * </ul>
     *
     * <p>If {@code desiredName} is null we generate {@code "<username>'s ship"}
     * for the first ship and {@code "<username>'s ship N"} for subsequent ones,
     * where N is the count + 1. The first ship keeps the un-numbered name so the
     * existing one-ship UX reads naturally.
     */
    @Transactional
    public Ship createForUser(UUID userId, String username, String desiredName) {
        long existing = shipRepository.countByUserId(userId);
        String name = desiredName != null ? desiredName : autoName(username, existing);
        Ship ship = new Ship(userId, name, SPAWN_X, SPAWN_Y);
        return shipRepository.save(ship);
    }

    @Transactional(readOnly = true)
    public List<ShipDto> listForUser(UUID userId) {
        return shipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ShipDto::from)
                .toList();
    }

    /**
     * Ownership-checked lookup used by ship-scoped endpoints (orders etc.). Throws
     * 404 if the ship doesn't exist or belongs to someone else — deliberately
     * indistinguishable from the outside so we don't leak the existence of other
     * users' ships.
     */
    @Transactional(readOnly = true)
    public Ship requireOwnedShip(UUID userId, UUID shipId) {
        return shipRepository.findByIdAndUserId(shipId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ship not found"));
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
