package org.example.springbootspacegame.ship;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipService {

    // Center of the 100x100 grid. Hard-coded for v1 — deterministic for tests and a
    // single source of truth for "where new players appear". Will move to WorldState
    // once issue #5 lands and grid size becomes a runtime value.
    static final int SPAWN_X = 50;
    static final int SPAWN_Y = 50;

    private final ShipRepository shipRepository;

    /**
     * Creates the user's starting mothership. Called from {@code AuthService.register}
     * inside the same transaction, so a failure here rolls the user creation back too.
     *
     * <p>v1 invariant: one ship per user. We re-check {@code existsByUserId} as
     * defense-in-depth — currently this can only be reached from a fresh registration,
     * but if {@code createForUser} ever gets called elsewhere this prevents a duplicate.
     */
    @Transactional
    public Ship createForUser(UUID userId, String username) {
        if (shipRepository.existsByUserId(userId)) {
            throw new IllegalStateException("User " + userId + " already has a ship");
        }
        Ship ship = new Ship(userId, defaultShipName(username), SPAWN_X, SPAWN_Y);
        return shipRepository.save(ship);
    }

    @Transactional(readOnly = true)
    public Ship getForUser(UUID userId) {
        return shipRepository.findByUserId(userId)
                // Should not happen for a registered user — auto-create runs in the same
                // transaction as user creation. Return 404 if it ever does, rather than 500.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No ship for user"));
    }

    private static String defaultShipName(String username) {
        return username + "'s ship";
    }
}
