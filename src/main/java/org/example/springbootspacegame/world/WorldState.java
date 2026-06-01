package org.example.springbootspacegame.world;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Singleton entity backed by the single row {@code id = 1} in {@code world_state}.
 * See DOMAIN.md "WorldState".
 *
 * <p>Read-mostly: the only mutation is the tick advance, done via a SQL UPDATE in
 * {@link WorldStateRepository#advanceTick()} rather than as a JPA setter+save, so
 * the increment is atomic on the DB side and doesn't depend on a read-modify-write
 * happening inside a single transaction.
 */
@Entity
@Table(name = "world_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class WorldState {

    /** Always {@code 1}. The DB has a CHECK constraint enforcing it. */
    @Id
    private Short id;

    @Column(name = "current_tick", nullable = false)
    private long currentTick;

    @Column(name = "last_tick_at", nullable = false)
    private OffsetDateTime lastTickAt;
}
