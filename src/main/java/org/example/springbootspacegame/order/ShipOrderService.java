package org.example.springbootspacegame.order;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipService;
import org.example.springbootspacegame.ship.ShipStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order queue per ship. Every public method requires both a {@code userId} and
 * a {@code shipId} — the {@link ShipService#requireOwnedShip} call resolves
 * ownership in one shot and 404s if the ship doesn't belong to the caller.
 *
 * <p>{@link #appendOrder} also runs the <b>auto-prerequisite</b> step from the
 * PR 2 design (issue #46 v3 comment): when the player's requested kind needs
 * the ship to be at a body (EXTRACT/SELL) or in flight (MOVE) and it isn't,
 * the matching prerequisite ({@code LAND} or {@code TAKE_OFF}) is inserted
 * ahead of the requested order in the queue, with {@code autoInserted = true}
 * so the UI can mark it.
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
     *
     * <p>Auto-prerequisite middleware kicks in based on the current ship status:
     * <ul>
     *   <li>EXTRACT or SELL while not at a body → prepend a {@code LAND}.</li>
     *   <li>MOVE while at a body → prepend a {@code TAKE_OFF}.</li>
     * </ul>
     * The inserted prerequisite gets {@code autoInserted = true} and a
     * {@code createdAt} one millisecond earlier than the player's order so the
     * queue's {@code ORDER BY created_at} keeps it first even when both rows
     * are saved in the same JVM tick.
     *
     * <p>Returns the DTO for the player's order — the auto-inserted one (if
     * any) is visible via the list endpoint but not in this response, so the
     * client's view of "the order I just submitted" stays unambiguous.
     */
    @Transactional
    public ShipOrderDto appendOrder(UUID userId, UUID shipId, CreateOrderRequest request) {
        Ship ship = shipService.requireOwnedShip(userId, shipId);
        handlerRegistry.forKind(request.kind()).validateParams(request.paramsOrEmpty());

        OrderKind requested = request.kind();
        OffsetDateTime base = OffsetDateTime.now();

        OrderKind prereq = autoPrerequisiteFor(ship, requested);
        if (prereq != null) {
            ShipOrder auto = new ShipOrder(ship.getId(), prereq, Map.of(), true);
            // 1 ms before the main order so ORDER BY created_at puts it first
            // even when both rows are persisted within the same wall-clock ms.
            auto.setCreatedAt(base.minusNanos(1_000_000L));
            orderRepository.save(auto);
        }

        ShipOrder order = new ShipOrder(
                ship.getId(),
                request.kind(),
                new HashMap<>(request.paramsOrEmpty()),
                false);
        order.setCreatedAt(base);
        return ShipOrderDto.from(orderRepository.save(order));
    }

    /**
     * Returns the prerequisite order kind to insert before {@code requested},
     * or {@code null} if no prerequisite is needed. Decides based on the
     * ship's current status — doesn't look at the queue. Multi-step queues
     * where the eventual status differs from the current one are handled by
     * the handler-level cancel: if EXTRACT later finds the ship in the wrong
     * state, it cancels with a clear reason rather than silently misbehaving.
     */
    private OrderKind autoPrerequisiteFor(Ship ship, OrderKind requested) {
        // Use the positional variant so a queued order earlier in the queue
        // doesn't confuse the auto-prerequisite decision — only "is the ship
        // at a body right now" matters.
        ShipStatus status = shipService.positionalStatusOf(ship);
        boolean atBody = status == ShipStatus.LANDED || status == ShipStatus.ORBITING;
        return switch (requested) {
            case EXTRACT, SELL -> atBody ? null : OrderKind.LAND;
            case MOVE          -> atBody ? OrderKind.TAKE_OFF : null;
            case LAND, TAKE_OFF -> null;
        };
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
