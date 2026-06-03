package org.example.springbootspacegame.body;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Pre-seeded point of interest on the grid. Renamed from {@code Planet} in V8
 * to reflect that the taxonomy now spans planets, asteroids, gas giants, and
 * stars. See DOMAIN.md "CelestialBody".
 *
 * <p>v1 model: bodies are read-only game content (seeded in V9). No setters,
 * no app-level creation flow. Players can no longer mutate body state directly;
 * the {@code body_resources} table holds the mutable reserves (decremented by
 * the EXTRACT handler in PR 2).
 *
 * <p>Intentionally not annotated with {@code @Data}: equals/hashCode on JPA
 * entities is a trap.
 */
@Entity
@Table(name = "celestial_bodies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CelestialBody {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CelestialBodyKind kind;
}
