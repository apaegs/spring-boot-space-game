package org.example.springbootspacegame.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * The caller's ship's order queue. Singular {@code /api/ship/orders} because v1
 * has one ship per user — when fleet support arrives this becomes
 * {@code /api/ships/{shipId}/orders}.
 */
@RestController
@RequestMapping("/api/ship/orders")
@RequiredArgsConstructor
public class ShipOrderController {

    private final ShipOrderService orderService;

    @GetMapping
    public List<ShipOrderDto> list() {
        return orderService.listForUser(currentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipOrderDto append(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.appendOrder(currentUserId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        orderService.cancelOrder(currentUserId(), id);
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
