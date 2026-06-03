package org.example.springbootspacegame.ship;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Catalog row of a ship type. Stats per type — cargo capacity, extraction rate —
 * live here so adding a new type ({@code HAULER}, {@code SCOUT}, …) is a new
 * row, not a code change.
 *
 * <p>v1 ships exactly one row: {@code MOTHERSHIP}, seeded with the deterministic
 * UUID {@code 00000000-0000-0000-0000-000000000001} in V8 so the {@code ships.ship_type_id}
 * backfill can reference it.
 *
 * <p>The PR 2 EXTRACT handler reads {@code cargoCapacity} and {@code extractRate}
 * from the type to enforce per-tick extraction limits.
 */
@Entity
@Table(name = "ship_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ShipType {

    /** Well-known seed UUID for the v1 {@code MOTHERSHIP} type. */
    public static final UUID MOTHERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "cargo_capacity", nullable = false)
    private int cargoCapacity;

    @Column(name = "extract_rate", nullable = false)
    private int extractRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ShipType(UUID id, String code, String name, int cargoCapacity, int extractRate) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.cargoCapacity = cargoCapacity;
        this.extractRate = extractRate;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
