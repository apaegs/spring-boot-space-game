package org.example.springbootspacegame.world;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the world singleton. Used by clients to display tick counter + grid size
 * (e.g. the frontend tick indicator).
 */
@RestController
@RequestMapping("/api/world")
@RequiredArgsConstructor
public class WorldController {

    private static final short WORLD_ID = 1;

    private final WorldStateRepository worldStateRepository;

    @GetMapping
    public WorldDto getWorld() {
        WorldState state = worldStateRepository.findById(WORLD_ID)
                // Shouldn't happen — V3 seeds the singleton. If it does, fail loud rather
                // than auto-create, so a missing migration is visible.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "World state missing — V3 migration may not have run"));
        return WorldDto.from(state);
    }
}
