package org.example.springbootspacegame.planet;

import java.util.UUID;

public record PlanetDto(
        UUID id,
        int x,
        int y,
        String name,
        String description
) {
    public static PlanetDto from(Planet planet) {
        return new PlanetDto(
                planet.getId(),
                planet.getX(),
                planet.getY(),
                planet.getName(),
                planet.getDescription()
        );
    }
}
