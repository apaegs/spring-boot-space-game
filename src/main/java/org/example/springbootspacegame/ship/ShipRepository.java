package org.example.springbootspacegame.ship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipRepository extends JpaRepository<Ship, UUID> {

    /** All ships belonging to a user, oldest first (the auto-created mothership is first). */
    List<Ship> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Count ships per user. Used by {@link ShipService#createForUser} to generate
     * the default ship name with a sequential suffix.
     */
    long countByUserId(UUID userId);

    /**
     * Ownership-checked lookup — returns the ship only if it belongs to {@code userId}.
     * Returning empty for "ship exists but belongs to someone else" is deliberate: the
     * controller maps it to 404 so we don't leak the existence of other users' ships.
     */
    Optional<Ship> findByIdAndUserId(UUID id, UUID userId);
}
