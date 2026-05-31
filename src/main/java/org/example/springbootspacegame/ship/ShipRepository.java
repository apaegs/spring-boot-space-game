package org.example.springbootspacegame.ship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipRepository extends JpaRepository<Ship, UUID> {

    /**
     * v1 invariant: exactly one ship per user. When fleet support arrives this becomes
     * {@code findAllByUserId} or similar.
     */
    Optional<Ship> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
