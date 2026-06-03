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
 * Read-side service for celestial bodies. PR 2 adds mutation (EXTRACT
 * decrements reserves, SELL reads prices) — for now this is read-only.
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
     * Returns the body on the given tile, if any. Used by the LAND order
     * handler — entity (not DTO) because the caller is another service, not
     * the REST layer.
     */
    @Transactional(readOnly = true)
    public Optional<CelestialBody> findAt(int x, int y) {
        return celestialBodyRepository.findByXAndY(x, y);
    }
}
