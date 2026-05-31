package org.example.springbootspacegame.order;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps each {@link OrderKind} to its {@link OrderHandler}. Spring injects the
 * full {@code List<OrderHandler>} of every {@code @Component} implementing
 * the interface — the registry just indexes them by kind.
 *
 * <p>Fail loud at startup if two handlers claim the same kind, or if a kind
 * has no handler. Both are configuration bugs; better caught now than at the
 * first tick.
 */
@Service
public class OrderHandlerRegistry {

    private final Map<OrderKind, OrderHandler> byKind;

    public OrderHandlerRegistry(List<OrderHandler> handlers) {
        this.byKind = handlers.stream().collect(Collectors.toMap(
                OrderHandler::kind,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                            "Two OrderHandler beans claim the same kind: "
                                    + a.getClass().getName() + " and " + b.getClass().getName());
                }));
        // Cross-check that every declared kind has a handler — prevents the
        // "I added OrderKind.WAIT but forgot the handler class" footgun.
        for (OrderKind kind : OrderKind.values()) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalStateException("No OrderHandler bean for kind " + kind);
            }
        }
    }

    public OrderHandler forKind(OrderKind kind) {
        return byKind.get(kind);
    }
}
