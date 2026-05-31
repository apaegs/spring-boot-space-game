package org.example.springbootspacegame.world;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WorldStateRepository extends JpaRepository<WorldState, Short> {

    /**
     * Atomically increment the tick counter on the singleton row. Done as a single
     * UPDATE so concurrent callers can't lose increments via read-modify-write — the
     * DB serializes the row update. Returns the number of rows affected (always 1 in
     * a healthy system; the {@code @Modifying} contract requires us to expose it).
     *
     * <p>For v1 we run a single application instance, so {@link org.springframework.scheduling.annotation.Scheduled}
     * is the only caller. When/if we scale horizontally we'll need cross-node
     * coordination (Shedlock or similar) to avoid double-ticking — that's deferred.
     */
    @Modifying
    @Query(value = "UPDATE world_state SET current_tick = current_tick + 1, last_tick_at = now() WHERE id = 1",
           nativeQuery = true)
    int advanceTick();
}
