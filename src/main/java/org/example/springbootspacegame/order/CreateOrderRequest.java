package org.example.springbootspacegame.order;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for {@code POST /api/ship/orders}. Per-kind params validation
 * happens in the matching {@link OrderHandler#validateParams(Map)}.
 */
public record CreateOrderRequest(
        @NotNull OrderKind kind,
        Map<String, Object> params
) {
    public Map<String, Object> paramsOrEmpty() {
        return params == null ? Map.of() : params;
    }
}
