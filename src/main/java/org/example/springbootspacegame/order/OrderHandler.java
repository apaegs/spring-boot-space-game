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
     * Advance this order by one tick. May mutate the ship's state (e.g. MOVE
     * changes its position). The caller runs inside a {@code @Transactional}
     * boundary so mutations are auto-persisted on commit.
     */
    OrderResult processOneTick(Ship ship, ShipOrder order);
}
