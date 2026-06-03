package org.example.springbootspacegame.body;

import org.example.springbootspacegame.resource.ResourceKind;

import java.util.List;
import java.util.UUID;

/**
 * Response shape for {@code GET /api/bodies}. Replaces the v1 {@code PlanetDto}
 * with a wider shape: includes the body {@link CelestialBodyKind kind}, the
 * reserves it carries, and the resources it buys (with prices).
 *
 * <p>Frontend reads these flat lists to render reserves and buy prices in the
 * body details panel. Empty lists are valid — stars carry no reserves and buy
 * nothing.
 */
public record CelestialBodyDto(
        UUID id,
        int x,
        int y,
        String name,
        String description,
        CelestialBodyKind kind,
        List<ResourceReserve> reserves,
        List<ResourceBuyPrice> buyPrices
) {

    /** Per-resource reserve row inside a {@link CelestialBodyDto}. */
    public record ResourceReserve(ResourceKind kind, int reserve) {
        public static ResourceReserve from(BodyResource row) {
            return new ResourceReserve(row.getResourceKind(), row.getReserve());
        }
    }

    /** Per-resource buy-price row inside a {@link CelestialBodyDto}. */
    public record ResourceBuyPrice(ResourceKind kind, int pricePerUnit) {
        public static ResourceBuyPrice from(BodyBuyPrice row) {
            return new ResourceBuyPrice(row.getResourceKind(), row.getPricePerUnit());
        }
    }

    public static CelestialBodyDto from(
            CelestialBody body,
            List<BodyResource> reserves,
            List<BodyBuyPrice> buyPrices
    ) {
        return new CelestialBodyDto(
                body.getId(),
                body.getX(),
                body.getY(),
                body.getName(),
                body.getDescription(),
                body.getKind(),
                reserves.stream().map(ResourceReserve::from).toList(),
                buyPrices.stream().map(ResourceBuyPrice::from).toList()
        );
    }
}
