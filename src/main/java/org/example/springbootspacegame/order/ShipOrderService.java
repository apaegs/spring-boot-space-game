package org.example.springbootspacegame.order;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Order queue per ship. Every public method requires both a {@code userId} and
 * a {@code shipId} — the {@link ShipService#requireOwnedShip} call resolves
 * ownership in one shot and 404s if the ship doesn't belong to the caller.
 */
@Service
@RequiredArgsConstructor
public class ShipOrderService {

    /** Statuses the player-facing queue includes. Completed/cancelled stay in DB for audit. */
    private static final List<OrderStatus> VISIBLE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.ACTIVE);

    private final ShipOrderRepository orderRepository;
    private final ShipService shipService;
    private final OrderHandlerRegistry handlerRegistry;

    /**
     * Append a new order to the queue of the caller's ship {@code shipId}.
     *
     * <p>Validates the params via the matching handler — a bad MOVE (missing
     * x/y, out of bounds, etc.) returns 400 immediately rather than waiting
     * for the next tick to fail.
     */
    @Transactional
    public ShipOrderDto appendOrder(UUID userId, UUID shipId, CreateOrderRequest request) {
        Ship ship = shipService.requireOwnedShip(userId, shipId);
        handlerRegistry.forKind(request.kind()).validateParams(request.paramsOrEmpty());

        ShipOrder order = new ShipOrder(
                ship.getId(),
                request.kind(),
                new HashMap<>(request.paramsOrEmpty()));
        return ShipOrderDto.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<ShipOrderDto> listForShip(UUID userId, UUID shipId) {
        Ship ship = shipService.requireOwnedShip(userId, shipId);
        return orderRepository
                .findByShipIdAndStatusInOrderByCreatedAtAsc(ship.getId(), VISIBLE_STATUSES)
                .stream()
                .map(ShipOrderDto::from)
                .toList();
    }

    /**
     * Cancel one of the caller's pending or active orders on this ship.
     * Already-completed or already-cancelled orders return 404 (you can't
     * cancel history). Cross-ship attempts (order ID belongs to a different
     * ship of yours, or to someone else's ship) also return 404 for the same
     * non-leak reason as ship ownership.
     */
    @Transactional
    public void cancelOrder(UUID userId, UUID shipId, UUID orderId) {
        Ship ship = shipService.requireOwnedShip(userId, shipId);
        ShipOrder order = orderRepository.findByIdAndShipId(orderId, ship.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order is not active");
        }
        order.markCancelled();
    }
}
