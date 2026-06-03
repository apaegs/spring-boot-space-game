package org.example.springbootspacegame.body;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.springbootspacegame.resource.ResourceKind;

import java.util.UUID;

/**
 * The price this body pays per unit of a given resource. Composite PK
 * {@code (body_id, resource_kind)}.
 *
 * <p>Absence of a row = body doesn't buy this resource (the SELL handler in
 * PR 2 will cancel with that reason). Separate from {@link BodyResource} so
 * a body can both produce and buy distinct resources — the seed deliberately
 * distributes production and buying across different bodies to force travel.
 */
@Entity
@Table(name = "body_buy_prices")
@IdClass(BodyBuyPriceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class BodyBuyPrice {

    @Id
    @Column(name = "body_id", nullable = false, updatable = false)
    private UUID bodyId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_kind", nullable = false, updatable = false)
    private ResourceKind resourceKind;

    @Column(name = "price_per_unit", nullable = false)
    private int pricePerUnit;

    public BodyBuyPrice(UUID bodyId, ResourceKind resourceKind, int pricePerUnit) {
        // Mirrors the V8 DB CHECK (price_per_unit > 0). Failing in the
        // constructor surfaces the bug at the call site instead of at flush time.
        if (pricePerUnit <= 0) {
            throw new IllegalArgumentException("pricePerUnit must be > 0, was " + pricePerUnit);
        }
        this.bodyId = bodyId;
        this.resourceKind = resourceKind;
        this.pricePerUnit = pricePerUnit;
    }
}
