package org.example.springbootspacegame.ship;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response for {@code GET /api/ship}. We never serialize the JPA entity directly
 * (see CLAUDE.md "Entity vs DTO").
 */
public record ShipDto(
        UUID id,
        String name,
        int x,
        int y,
        Integer destinationX,
        Integer destinationY,
        OffsetDateTime createdAt
) {
    public static ShipDto from(Ship ship) {
        return new ShipDto(
                ship.getId(),
                ship.getName(),
                ship.getX(),
                ship.getY(),
                ship.getDestinationX(),
                ship.getDestinationY(),
                ship.getCreatedAt()
        );
    }
}
