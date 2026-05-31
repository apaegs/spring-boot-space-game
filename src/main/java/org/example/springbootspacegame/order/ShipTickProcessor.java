package org.example.springbootspacegame.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootspacegame.ship.Ship;
import org.example.springbootspacegame.ship.ShipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Per-ship tick processing. Separate class from the event listener so each
 * ship runs in its own transaction — one ship's failure can't roll back the
 * tick or affect another ship's progress.
 *
 * <p>The actual loop over ships lives in {@code ShipOrderTickListener}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipTickProcessor {

    private final ShipRepository shipRepository;
    private final ShipOrderRepository orderRepository;
    private final OrderHandlerRegistry handlerRegistry;

    /**
     * {@code REQUIRES_NEW} is load-bearing here. This method is invoked from
     * {@code ShipOrderTickListener.onTick}, which fires synchronously inside
     * {@code TickService.advanceTick()}'s {@code @Transactional} scope. With
     * the default {@code REQUIRED} propagation, processOneShip would join
     * that outer transaction — a per-ship failure would mark the *tick*
     * transaction rollback-only, undoing the tick counter advance and every
     * other ship's work. {@code REQUIRES_NEW} suspends the outer transaction
     * and gives each ship its own physical one, so the per-ship error
     * containment in the listener actually works as the comment there
     * claims.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOneShip(UUID shipId) {
        Ship ship = shipRepository.findById(shipId).orElse(null);
        if (ship == null) {
            return; // Ship was deleted between listing and processing — silently skip.
        }
        ShipOrder head = orderRepository
                .findFirstByShipIdAndStatusInOrderByCreatedAtAsc(
                        shipId, java.util.List.of(OrderStatus.PENDING, OrderStatus.ACTIVE))
                .orElse(null);
        if (head == null) {
            return; // No queued work; nothing to do this tick.
        }

        // First time we pick up a PENDING order, flip it to ACTIVE so the
        // started_at timestamp is meaningful.
        if (head.getStatus() == OrderStatus.PENDING) {
            head.markActive();
        }

        OrderResult result = handlerRegistry.forKind(head.getKind()).processOneTick(ship, head);

        switch (result) {
            case OrderResult.InProgress ignored -> {
                // Stays ACTIVE; the next tick continues advancing it.
            }
            case OrderResult.Completed ignored -> {
                head.markCompleted();
                log.info("Ship {} completed {} order {}", shipId, head.getKind(), head.getId());
            }
            case OrderResult.Cancelled c -> {
                head.markCancelled();
                log.info("Ship {} cancelled {} order {}: {}",
                        shipId, head.getKind(), head.getId(), c.reason());
            }
        }
    }
}
