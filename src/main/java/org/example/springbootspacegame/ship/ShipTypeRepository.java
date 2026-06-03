package org.example.springbootspacegame.ship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipTypeRepository extends JpaRepository<ShipType, UUID> {

    /** Lookup by the catalog code (e.g. {@code "MOTHERSHIP"}). */
    Optional<ShipType> findByCode(String code);
}
