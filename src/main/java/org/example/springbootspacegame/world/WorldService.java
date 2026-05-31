package org.example.springbootspacegame.world;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WorldService {

    private static final short WORLD_ID = 1;

    private final WorldStateRepository worldStateRepository;

    /**
     * Read the singleton world state and return it as a DTO. The entity-to-DTO
     * mapping lives here (not the controller) per the CLAUDE.md "Entity vs DTO"
     * rule.
     */
    @Transactional(readOnly = true)
    public WorldDto getWorld() {
        WorldState state = worldStateRepository.findById(WORLD_ID)
                // Shouldn't happen — V3 seeds the singleton. If it does, fail loud rather
                // than auto-create, so a missing migration is visible.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "World state missing — V3 migration may not have run"));
        return WorldDto.from(state);
    }
}
