package org.example.springbootspacegame.world;

import java.time.OffsetDateTime;

/**
 * API response for {@code GET /api/world}.
 */
public record WorldDto(
        long currentTick,
        OffsetDateTime lastTickAt,
        int gridWidth,
        int gridHeight
) {
    public static WorldDto from(WorldState state) {
        return new WorldDto(
                state.getCurrentTick(),
                state.getLastTickAt(),
                state.getGridWidth(),
                state.getGridHeight()
        );
    }
}
