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
     * params. Override and throw {@code ResponseStatusException}
     * with BAD_REQUEST if the params are malformed.
     */
    default void validateParams(java.util.Map<String, Object> params) {
        // no-op
    }

    /**
     * Append-time validation that needs context beyond the params themselves —
     * the ship, the world state, other ships. Runs after {@link #validateParams}
     * inside the {@code appendOrder} transaction. Default no-op. Override and
     * throw {@code ResponseStatusException} with BAD_REQUEST when the order
     * can't be accepted given the current state.
     *
     * <p>Distinct from {@link #validateParams} so that pure shape-validation
     * stays DB-free and easy to unit-test.
     */
    default void validateForShip(Ship ship, java.util.Map<String, Object> params) {
        // no-op
    }

    /**
     * Advance this order by one tick. May mutate the ship's state (e.g. MOVE
     * changes its position). The caller runs inside a {@code @Transactional}
     * boundary so mutations are auto-persisted on commit.
     */
    OrderResult processOneTick(Ship ship, ShipOrder order);
}
