package org.example.springbootspacegame.ship;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;



/**
 * Multi-ship endpoints. {@code /api/ships} returns the caller's fleet;
 * {@code POST} adds a new ship. Ship-scoped subresources (orders) live under
 * {@code /api/ships/{shipId}/...} — see {@code ShipOrderController}.
 *
 * <p>Thin per the CLAUDE.md layering rule — all logic, including the user
 * lookup and DTO mapping, lives in {@link ShipService}.
 */
@RestController
@RequestMapping("/api/ships")
@RequiredArgsConstructor
public class ShipController {

    private final ShipService shipService;

    @GetMapping
    public List<ShipDto> myShips() {
        return shipService.listForUser(currentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipDto createShip(@Valid @RequestBody(required = false) CreateShipRequest request) {
        return shipService.createShipForCurrentUser(currentUserId(), request);
    }

    @PatchMapping("/{id}")
    public ShipDto renameShip(@PathVariable UUID id,
                              @Valid @RequestBody RenameShipRequest request) {
        return shipService.renameShip(currentUserId(), id, request.name());
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
