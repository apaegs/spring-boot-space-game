package org.example.springbootspacegame.order;

import org.example.springbootspacegame.ship.Ship;

/**
 * Strategy for one {@link OrderKind}. Implementations are Spring beans;
 * {@link OrderHandlerRegistry} auto-collects them and dispatches by kind.
 *
 * <p>Adding a new kind is: add the enum value to {@link OrderKind}, implement
 * this interface in a new {@code @Component}. No changes to the processor,
 * the registry, or the schema.
 */
public interface OrderHandler {

    OrderKind kind();

    /**
     * Validate the params payload at append-time so bad orders 400 immediately
     * instead of failing at tick time. Default no-op for handlers that take no
     * params (e.g. LAND). Override and throw {@code ResponseStatusException}
     * with BAD_REQUEST if the params are malformed.
     */
    default void validateParams(java.util.Map<String, Object> params) {
        // no-op
    }

    /**
     * Advance this order by one tick. May mutate the ship's state (e.g. MOVE
     * changes its position). The caller runs inside a {@code @Transactional}
     * boundary so mutations are auto-persisted on commit.
     */
    OrderResult processOneTick(Ship ship, ShipOrder order);
}
