package org.example.springbootspacegame.body;

import org.example.springbootspacegame.resource.ResourceKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BodyBuyPriceRepository extends JpaRepository<BodyBuyPrice, BodyBuyPriceId> {

    /**
     * All buy-price rows for a body. Used when rendering body details.
     */
    List<BodyBuyPrice> findByBodyId(UUID bodyId);

    /**
     * Price for a specific (body, resource) — used by the SELL handler (PR 2)
     * to compute the credit total.
     */
    Optional<BodyBuyPrice> findByBodyIdAndResourceKind(UUID bodyId, ResourceKind resourceKind);
}
