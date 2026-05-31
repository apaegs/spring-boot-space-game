package org.example.springbootspacegame.tick;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootspacegame.world.WorldState;
import org.example.springbootspacegame.world.WorldStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the tick lifecycle: advance the world clock, then dispatch the hook for
 * per-tick game logic (ship movement, future order processing, etc.).
 *
 * <p>The hook is intentionally an inline placeholder in v1. When {@code #11}
 * (ship movement) lands it'll either: (a) become a method that calls
 * {@code shipMovementService.advanceMotion(tick)} directly, or (b) be replaced
 * with Spring's {@code ApplicationEventPublisher} so any number of per-tick
 * services can listen without {@code TickService} knowing about them. Both
 * patterns work; pick when the second listener appears.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TickService {

    private static final short WORLD_ID = 1;

    private final WorldStateRepository worldStateRepository;

    /**
     * Advance the world by one tick and run per-tick hooks. Called by
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
        onTick(tick);
        return tick;
    }

    /**
     * Per-tick hook. Empty for v1 — ship movement etc. will plug in here in #11.
     */
    private void onTick(long tickNumber) {
        log.debug("Tick {}", tickNumber);
    }
}
