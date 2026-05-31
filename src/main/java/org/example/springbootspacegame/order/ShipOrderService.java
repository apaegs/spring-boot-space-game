package org.example.springbootspacegame.order;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipOrderService {

    /** Statuses the player-facing queue includes. Completed/cancelled stay in DB for audit. */
    private static final List<OrderStatus> VISIBLE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.ACTIVE);

    private final ShipOrderRepository orderRepository;
    private final ShipRepository shipRepository;
    private final OrderHandlerRegistry handlerRegistry;

    /**
     * Append a new order to the caller's ship's queue.
     *
     * <p>Validates the params via the matching handler — a bad MOVE (missing
     * x/y, out of bounds, etc.) returns 400 immediately rather than waiting
     * for the next tick to fail.
     */
    @Transactional
    public ShipOrderDto appendOrder(UUID userId, CreateOrderRequest request) {
        Ship ship = requireShipFor(userId);
        handlerRegistry.forKind(request.kind()).validateParams(request.paramsOrEmpty());

        ShipOrder order = new ShipOrder(
                ship.getId(),
                request.kind(),
                new HashMap<>(request.paramsOrEmpty()));
        return ShipOrderDto.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<ShipOrderDto> listForUser(UUID userId) {
        Ship ship = requireShipFor(userId);
        return orderRepository
                .findByShipIdAndStatusInOrderByCreatedAtAsc(ship.getId(), VISIBLE_STATUSES)
                .stream()
                .map(ShipOrderDto::from)
                .toList();
    }

    /**
     * Cancel one of the caller's pending or active orders. Already-completed
     * or already-cancelled orders return 404 (you can't cancel history).
     */
    @Transactional
    public void cancelOrder(UUID userId, UUID orderId) {
        Ship ship = requireShipFor(userId);
        ShipOrder order = orderRepository.findByIdAndShipId(orderId, ship.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order is not active");
        }
        order.markCancelled();
    }

    private Ship requireShipFor(UUID userId) {
        return shipRepository.findByUserId(userId)
                // Shouldn't happen — register() always creates a ship in the same transaction.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No ship for user"));
    }
}
