package org.example.springbootspacegame.ship;

import java.util.UUID;

/**
 * Public projection of a {@link Ship} for the world-wide ship listing
 * ({@code GET /api/world/ships}). Deliberately narrower than {@link ShipDto}:
 * no {@code userId}, no {@code createdAt}, no order info.
 *
 * <p>Why a separate record instead of reusing {@code ShipDto}: keeping the
 * privacy boundary explicit at the type level makes it impossible to
 * accidentally widen what other players can see. Adding a field to
 * {@code ShipDto} for an owner-only feature won't leak through here.
 */
public record PublicShipDto(
        UUID id,
        String name,
        int x,
        int y
) {
    public static PublicShipDto from(Ship ship) {
        return new PublicShipDto(ship.getId(), ship.getName(), ship.getX(), ship.getY());
    }
}
