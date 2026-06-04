package org.example.springbootspacegame.ship;

import org.example.springbootspacegame.resource.ResourceKind;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API response for {@code GET /api/ships}. We never serialize the JPA entity
 * directly (see CLAUDE.md "Entity vs DTO").
 *
 * <p>{@link ShipStatus} is derived at read time in {@link ShipService} — it is
 * NOT stored as a DB column. Use {@link #from(Ship, ShipStatus, ShipType, List)}
 * when constructing from the service.
 *
 * <p>Ship-type stats ({@code shipTypeName}, {@code cargoCapacity}) and the
 * per-resource {@code cargo} list are joined in here so the UI can render the
 * ship-info panel from a single payload — avoids a /ships → /ship-types → /cargo
 * fanout per poll cycle.
 */
public record ShipDto(
        UUID id,
        String name,
        int x,
        int y,
        UUID shipTypeId,
        String shipTypeName,
        int cargoCapacity,
        int extractRate,
        List<CargoEntry> cargo,
        OffsetDateTime createdAt,
        ShipStatus status
) {
    /** Per-resource cargo row inside the ShipDto. */
    public record CargoEntry(ResourceKind resourceKind, int qty) {
        public static CargoEntry from(ShipCargo row) {
            return new CargoEntry(row.getResourceKind(), row.getQty());
        }
    }

    public static ShipDto from(Ship ship, ShipStatus status, ShipType type, List<ShipCargo> cargo) {
        return new ShipDto(
                ship.getId(),
                ship.getName(),
                ship.getX(),
                ship.getY(),
                ship.getShipTypeId(),
                type.getName(),
                type.getCargoCapacity(),
                type.getExtractRate(),
                cargo.stream().map(CargoEntry::from).toList(),
                ship.getCreatedAt(),
                status
        );
    }
}
