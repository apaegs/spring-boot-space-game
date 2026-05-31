package org.example.springbootspacegame.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootspacegame.tick.TickEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Wiring: when a tick fires, find ships with queued work and dispatch
 * per-ship processing. Each ship runs in {@link ShipTickProcessor#processOneShip}
 * — its own transaction — so one bad ship can't break the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipOrderTickListener {

    /** Statuses that mean "there's still work to do on this order". */
    private static final List<OrderStatus> ACTIVE_OR_PENDING =
            List.of(OrderStatus.PENDING, OrderStatus.ACTIVE);

    private final ShipOrderRepository orderRepository;
    private final ShipTickProcessor shipTickProcessor;

    @EventListener
    public void onTick(TickEvent event) {
        List<UUID> shipIds = orderRepository.findDistinctShipIdsByStatusIn(ACTIVE_OR_PENDING);
        if (shipIds.isEmpty()) {
            return;
        }
        log.debug("Tick {}: advancing orders for {} ship(s)", event.tick(), shipIds.size());
        for (UUID shipId : shipIds) {
            try {
                shipTickProcessor.processOneShip(shipId);
            } catch (RuntimeException e) {
                // Log and continue — a broken handler shouldn't poison other ships'
                // ticks. The transaction inside processOneShip rolls back, so the
                // bad ship's state is unchanged.
                log.error("Failed to process tick for ship {}", shipId, e);
            }
        }
    }
}
