package org.example.springbootspacegame.ship;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * v1: singular {@code /api/ship} returns the caller's only mothership.
 * When fleet support arrives this becomes {@code /api/ships} (URL-breaking change
 * accepted — see DOMAIN.md "Forward-compat").
 */
@RestController
@RequestMapping("/api/ship")
@RequiredArgsConstructor
public class ShipController {

    private final ShipService shipService;

    @GetMapping
    public ShipDto myShip() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return shipService.getForUser(principal.getUserId());
    }
}
