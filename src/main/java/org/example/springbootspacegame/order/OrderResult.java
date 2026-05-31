package org.example.springbootspacegame.order;

/**
 * What a handler returns after processing one tick of an order.
 *
 * <p>Sealed so the processor's {@code switch} stays exhaustive when we add
 * a new variant (e.g. {@code Failed(String)} vs {@code Cancelled(String)},
 * or {@code Yielded} for "I want to step out, let the next order in queue go").
 */
public sealed interface OrderResult {

    /** The order still has work to do — keep it in ACTIVE for the next tick. */
    record InProgress() implements OrderResult {}

    /** The order is done — mark COMPLETED and move on to the next order in queue. */
    record Completed() implements OrderResult {}

    /**
     * The order can't be fulfilled (e.g. LAND issued while not on a planet tile).
     * Mark CANCELLED with the reason in the log so the player has *something* to read.
     */
    record Cancelled(String reason) implements OrderResult {}

    static OrderResult inProgress() {
        return new InProgress();
    }

    static OrderResult completed() {
        return new Completed();
    }

    static OrderResult cancelled(String reason) {
        return new Cancelled(reason);
    }
}
