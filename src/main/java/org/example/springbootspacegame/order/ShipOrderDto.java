package org.example.springbootspacegame.order;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * API response for the order endpoints. The {@code params} shape is
 * order-kind-specific — the frontend renders different UIs based on
 * {@link #kind}.
 */
public record ShipOrderDto(
        UUID id,
        UUID shipId,
        OrderKind kind,
        Map<String, Object> params,
        OrderStatus status,
        /** Counter incremented by multi-tick handlers per tick of progress. */
        int progressTicks,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public static ShipOrderDto from(ShipOrder order) {
        return new ShipOrderDto(
                order.getId(),
                order.getShipId(),
                order.getKind(),
                order.getParams(),
                order.getStatus(),
                order.getProgressTicks(),
                order.getCreatedAt(),
                order.getStartedAt(),
                order.getCompletedAt()
        );
    }
}
