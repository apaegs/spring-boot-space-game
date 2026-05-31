package org.example.springbootspacegame.ship;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response for {@code GET /api/ship}. We never serialize the JPA entity directly
 * (see CLAUDE.md "Entity vs DTO").
 *
 * <p>What the ship is currently doing lives in the order queue
 * ({@code GET /api/ship/orders}), not on this DTO.
 */
public record ShipDto(
        UUID id,
        String name,
        int x,
        int y,
        OffsetDateTime createdAt
) {
    public static ShipDto from(Ship ship) {
        return new ShipDto(
                ship.getId(),
                ship.getName(),
                ship.getX(),
                ship.getY(),
                ship.getCreatedAt()
        );
    }
}
