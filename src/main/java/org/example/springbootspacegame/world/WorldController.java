package org.example.springbootspacegame.world;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.ship.PublicShipDto;
import org.example.springbootspacegame.ship.ShipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * World-scoped read endpoints — things every authenticated player sees the
 * same way: the tick counter and grid size ({@code GET /api/world}), the
 * world-wide ship listing ({@code GET /api/world/ships}).
 *
 * <p>The ship listing lives here rather than under {@code /api/ships} on
 * purpose: that controller is the caller's *own* fleet (private DTO with
 * createdAt etc.). This is a separate, narrower public projection.
 */
@RestController
@RequestMapping("/api/world")
@RequiredArgsConstructor
public class WorldController {

    private final WorldService worldService;
    private final ShipService shipService;

    @GetMapping
    public WorldDto getWorld() {
        return worldService.getWorld();
    }

    @GetMapping("/ships")
    public List<PublicShipDto> listShips() {
        return shipService.listAllPublic();
    }
}
