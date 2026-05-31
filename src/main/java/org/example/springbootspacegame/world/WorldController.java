package org.example.springbootspacegame.world;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the world singleton. Used by clients to display tick counter + grid size
 * (e.g. the frontend tick indicator).
 */
@RestController
@RequestMapping("/api/world")
@RequiredArgsConstructor
public class WorldController {

    private final WorldService worldService;

    @GetMapping
    public WorldDto getWorld() {
        return worldService.getWorld();
    }
}
