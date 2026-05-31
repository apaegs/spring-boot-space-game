package org.example.springbootspacegame.tick;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires {@link TickService#advanceTick()} on a fixed interval defined by
 * {@code game.tick.interval-ms} in {@code application.properties}.
 *
 * <p>This is a thin wrapper — the actual logic lives in {@link TickService} so
 * tests can call {@code advanceTick()} directly without waiting for the cron.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TickScheduler {

    private final TickService tickService;

    // initialDelay matches the fixed delay so the first tick doesn't fire
    // immediately on startup. In production this just defers the first tick by
    // one interval. In tests we override game.tick.interval-ms to ~1 hour via
    // src/test/resources/application.properties, which effectively disables the
    // scheduler — tests call TickService.advanceTick() directly for determinism.
    // Without this, the scheduler could race tests' world_state writes and
    // either deadlock (cleanup.sql vs advanceTick) or bump currentTick past
    // what a test expects.
    @Scheduled(fixedDelayString = "${game.tick.interval-ms}",
               initialDelayString = "${game.tick.interval-ms}")
    void tick() {
        try {
            long tick = tickService.advanceTick();
            log.info("World tick {}", tick);
        } catch (Exception e) {
            // Catch-and-log so one bad tick doesn't kill the scheduler thread —
            // @Scheduled stops firing if the method throws.
            log.error("Tick failed", e);
        }
    }
}
