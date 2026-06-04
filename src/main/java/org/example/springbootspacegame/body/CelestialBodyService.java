package org.example.springbootspacegame.body;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-side service for celestial bodies. EXTRACT decrements reserves and SELL
 * reads prices through their own repositories — this service exposes the bulk
 * map fetch and the per-tile adjacency lookups callers need.
 *
 * <p>The {@link #getAll()} path fetches bodies + reserves + buy prices in three
 * queries and groups in memory. The map view polls this endpoint, so an N+1
 * per-body fetch would scale poorly as the body count grows.
 */
@Service
@RequiredArgsConstructor
public class CelestialBodyService {

    private final CelestialBodyRepository celestialBodyRepository;
    private final BodyResourceRepository bodyResourceRepository;
    private final BodyBuyPriceRepository bodyBuyPriceRepository;

    @Transactional(readOnly = true)
    public List<CelestialBodyDto> getAll() {
        List<CelestialBody> bodies = celestialBodyRepository.findAll();

        // Single fetch of every reserve / buy-price row, grouped by body_id.
        // Avoids the N+1 that a per-body subquery would produce on /api/bodies.
        Map<UUID, List<BodyResource>> reservesByBody = bodyResourceRepository.findAll().stream()
                .collect(Collectors.groupingBy(BodyResource::getBodyId));
        Map<UUID, List<BodyBuyPrice>> pricesByBody = bodyBuyPriceRepository.findAll().stream()
                .collect(Collectors.groupingBy(BodyBuyPrice::getBodyId));

        return bodies.stream()
                .map(body -> CelestialBodyDto.from(
                        body,
                        reservesByBody.getOrDefault(body.getId(), List.of()),
                        pricesByBody.getOrDefault(body.getId(), List.of())))
                .toList();
    }

    /**
     * Every celestial body Chebyshev-adjacent to {@code (x, y)} — the 8
     * neighbouring tiles, excluding the center — in deterministic order. Used
     * by status derivation and by the EXTRACT / SELL handlers. The caller is
     * expected to be another service, hence the entity (not DTO) return type.
     * List, not Optional, because a ship can be adjacent to more than one body
     * — handlers pick one off the front; status derivation only needs "is the
     * list non-empty".
     *
     * <p>The center filter matches the {@code Math.max(|dx|, |dy|) === 1}
     * predicate the frontend's {@code bodyAtSelectedShip} uses, so backend
     * and UI agree on which body counts as "the body the ship is orbiting"
     * even if a ship ever ends up on a body's tile.
     */
    @Transactional(readOnly = true)
    public List<CelestialBody> findAdjacent(int x, int y) {
        return celestialBodyRepository
                .findByXBetweenAndYBetweenOrderByXAscYAscIdAsc(x - 1, x + 1, y - 1, y + 1)
                .stream()
                .filter(body -> body.getX() != x || body.getY() != y)
                .toList();
    }

    /**
     * Convenience for callers that just want the first adjacent body — the
     * deterministic pick when there's a choice.
     */
    @Transactional(readOnly = true)
    public Optional<CelestialBody> findFirstAdjacent(int x, int y) {
        List<CelestialBody> adjacent = findAdjacent(x, y);
        return adjacent.isEmpty() ? Optional.empty() : Optional.of(adjacent.get(0));
    }
}
