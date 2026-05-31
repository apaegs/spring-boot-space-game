package org.example.springbootspacegame.planet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Pre-seeded point of interest on the grid. See DOMAIN.md "Planet".
 *
 * <p>v1 model: planets are read-only game content (seeded in V5). No setters,
 * no app-level creation flow.
 */
@Entity
@Table(name = "planets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class Planet {

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
}
