package org.example.springbootspacegame.tick;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootspacegame.world.WorldState;
import org.example.springbootspacegame.world.WorldStateRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the tick lifecycle: advance the world clock, then publish a {@link TickEvent}
 * so per-tick subsystems (ship order processing, future systems) can do their work.
 *
 * <p>The event is published synchronously within this method's transaction, but each
 * listener that mutates DB state should open its own transaction so one listener's
 * failure doesn't roll back the tick or other listeners' progress
 * (see {@code ShipTickProcessor}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TickService {

    private static final short WORLD_ID = 1;

    private final WorldStateRepository worldStateRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Advance the world by one tick and notify per-tick listeners. Called by
     * {@link TickScheduler} on a cron, and by tests directly for deterministic
     * verification (no waiting for the scheduler).
     */
    @Transactional
    public long advanceTick() {
        worldStateRepository.advanceTick();
        // findById after the UPDATE so we read the post-increment value. Same
        // transaction = same snapshot.
        WorldState state = worldStateRepository.findById(WORLD_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "World state missing — V3 migration may not have run"));
        long tick = state.getCurrentTick();
        log.debug("Tick {}", tick);
        eventPublisher.publishEvent(new TickEvent(tick, state.getLastTickAt()));
        return tick;
    }
}
