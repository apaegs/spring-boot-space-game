package org.example.springbootspacegame.ship;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response for {@code GET /api/ship}. We never serialize the JPA entity directly
 * (see CLAUDE.md "Entity vs DTO").
 *
 * <p>{@link ShipStatus} is derived at read time in {@link ShipService} — it is NOT stored
 * as a DB column. Use {@link #from(Ship, ShipStatus)} when constructing from the service.
 */
public record ShipDto(
        UUID id,
        String name,
        int x,
        int y,
        UUID shipTypeId,
        OffsetDateTime createdAt,
        ShipStatus status
) {
    public static ShipDto from(Ship ship, ShipStatus status) {
        return new ShipDto(
                ship.getId(),
                ship.getName(),
                ship.getX(),
                ship.getY(),
                ship.getShipTypeId(),
                ship.getCreatedAt(),
                status
        );
    }
}
