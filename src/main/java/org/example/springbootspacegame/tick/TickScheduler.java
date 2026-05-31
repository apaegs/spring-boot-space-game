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

    @Scheduled(fixedDelayString = "${game.tick.interval-ms}")
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
