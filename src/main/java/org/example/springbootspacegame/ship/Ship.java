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

    /**
     * FK into {@code ship_types}. v1 ships all spawn as
     * {@link ShipType#MOTHERSHIP_ID MOTHERSHIP}; future PRs introduce a
     * type-picker at create time. Plain UUID rather than {@code @ManyToOne}
     * — readers that need the type's stats (cargo cap, extract rate) go
     * through {@link ShipTypeRepository}.
     */
    @Column(name = "ship_type_id", nullable = false, updatable = false)
    private UUID shipTypeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Ship(UUID userId, String name, int x, int y, UUID shipTypeId) {
        this.userId = userId;
        this.name = name;
        this.x = x;
        this.y = y;
        this.shipTypeId = shipTypeId;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /**
     * Set a new position. Callers must keep {@code (x, y)} inside the grid
     * (see {@code WorldConstants.GRID_SIZE}); the DB CHECK constraints back this
     * up and will throw at flush time on out-of-range values. Used by
     * MoveOrderHandler inside the tick processor's transaction.
     */
    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void rename(String name) {
        this.name = name;
    }
}
