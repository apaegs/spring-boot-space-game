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
 * Ship-scoped order queue. The {@code {shipId}} path param is ownership-checked
 * in {@link ShipOrderService#appendOrder} / {@code listForShip} / {@code cancelOrder}
 * via {@code ShipService.requireOwnedShip} — 404 if the ship doesn't belong to
 * the caller, indistinguishable from "ship doesn't exist".
 */
@RestController
@RequestMapping("/api/ships/{shipId}/orders")
@RequiredArgsConstructor
public class ShipOrderController {

    private final ShipOrderService orderService;

    @GetMapping
    public List<ShipOrderDto> list(@PathVariable UUID shipId) {
        return orderService.listForShip(currentUserId(), shipId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipOrderDto append(@PathVariable UUID shipId,
                               @Valid @RequestBody CreateOrderRequest request) {
        return orderService.appendOrder(currentUserId(), shipId, request);
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID shipId, @PathVariable UUID orderId) {
        orderService.cancelOrder(currentUserId(), shipId, orderId);
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
