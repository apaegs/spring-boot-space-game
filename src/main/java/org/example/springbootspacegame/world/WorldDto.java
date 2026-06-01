package org.example.springbootspacegame.world;

import java.time.OffsetDateTime;

/**
 * API response for {@code GET /api/world}.
 *
 * <p>Grid size is intentionally not on this DTO — it's a compile-time constant
 * shared by client and server (see {@code WorldConstants.GRID_SIZE} on the
 * backend and the {@code GRID_SIZE} export on the frontend). Issue #29.
 */
public record WorldDto(
        long currentTick,
        OffsetDateTime lastTickAt
) {
    public static WorldDto from(WorldState state) {
        return new WorldDto(
                state.getCurrentTick(),
                state.getLastTickAt()
        );
    }
}
