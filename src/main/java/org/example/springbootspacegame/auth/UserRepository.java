package org.example.springbootspacegame.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Pessimistic-write lock on the user row, held for the rest of the
     * calling transaction. Used by {@code ShipService.createShipForCurrentUser}
     * to serialize concurrent ship creates for the same user — otherwise two
     * near-simultaneous {@code POST /api/ships} could both compute the same
     * auto-name from {@code countByUserId}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
