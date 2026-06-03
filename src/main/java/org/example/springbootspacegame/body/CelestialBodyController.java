package org.example.springbootspacegame.body;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the celestial body catalogue. Replaces {@code /api/planets}
 * in V8 — the renamed endpoint reflects that the catalogue now spans planets,
 * asteroids, gas giants, and stars (see {@link CelestialBodyKind}).
 */
@RestController
@RequestMapping("/api/bodies")
@RequiredArgsConstructor
public class CelestialBodyController {

    private final CelestialBodyService celestialBodyService;

    @GetMapping
    public List<CelestialBodyDto> list() {
        return celestialBodyService.getAll();
    }
}
