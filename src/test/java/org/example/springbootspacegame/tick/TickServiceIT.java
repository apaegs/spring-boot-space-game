package org.example.springbootspacegame.tick;

import org.example.springbootspacegame.IntegrationTest;
import org.example.springbootspacegame.world.WorldStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TickServiceIT {

    private static final short WORLD_ID = 1;

    @Autowired
    private TickService tickService;

    @Autowired
    private WorldStateRepository worldStateRepository;

    @Test
    void advanceTickIncrementsCounterAndTimestamp() {
        var before = worldStateRepository.findById(WORLD_ID).orElseThrow();
        long startTick = before.getCurrentTick();
        var startTime = before.getLastTickAt();

        long tickAfterFirst = tickService.advanceTick();
        long tickAfterSecond = tickService.advanceTick();

        assertThat(tickAfterFirst).isEqualTo(startTick + 1);
        assertThat(tickAfterSecond).isEqualTo(startTick + 2);

        var after = worldStateRepository.findById(WORLD_ID).orElseThrow();
        assertThat(after.getCurrentTick()).isEqualTo(startTick + 2);
        assertThat(after.getLastTickAt()).isAfterOrEqualTo(startTime);
    }
}
