package org.example.springbootspacegame.tick;

import java.time.OffsetDateTime;

/**
 * Published by {@link TickService} after each tick advances. Per-tick game logic
 * (ship order processing, future systems) subscribes via {@code @EventListener}.
 *
 * <p>{@code tick} is the new {@code current_tick} value, post-increment, so a
 * listener can log "processing tick 42" and have it match the DB.
 */
public record TickEvent(long tick, OffsetDateTime at) {
}
