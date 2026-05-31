package org.example.springbootspacegame.ship;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mothership. See DOMAIN.md.
 *
 * <p>v1: exactly one per User, enforced in app logic. The schema (no UNIQUE on user_id)
 * is forward-compatible with fleet support.
 *
 * <p>Intentionally not annotated with {@code @Data}: equals/hashCode on JPA entities is a trap.
 */
@Entity
@Table(name = "ships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class Ship {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    // destination_x/y removed in V4 — what a ship is currently doing lives in
    // the ship_orders queue, not on the ship row. See DOMAIN.md "ShipOrder".

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Ship(UUID userId, String name, int x, int y) {
        this.userId = userId;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /**
     * Set a new position. The DB enforces 0 ≤ x,y < 100 via CHECK constraints —
     * passing out-of-range values throws at flush time. Used by MoveOrderHandler
     * inside the tick processor's transaction.
     */
    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
