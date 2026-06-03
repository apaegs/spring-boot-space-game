package org.example.springbootspacegame.ship;

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
 * Per-ship per-resource cargo row. Composite PK {@code (ship_id, resource_kind)}.
 *
 * <p>One row per (ship, resource_kind) with {@code qty > 0}. The EXTRACT handler
 * (PR 2) upserts these rows when extracting; the SELL handler removes them when
 * the cargo of that kind is sold.
 *
 * <p>Cargo cap is enforced against the {@code SUM(qty)} for a given ship — not
 * per-row — so the cap is "total units across all resources combined" per the
 * design.
 */
@Entity
@Table(name = "ship_cargo")
@IdClass(ShipCargoId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ShipCargo {

    @Id
    @Column(name = "ship_id", nullable = false, updatable = false)
    private UUID shipId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_kind", nullable = false, updatable = false)
    private ResourceKind resourceKind;

    @Column(nullable = false)
    private int qty;

    public ShipCargo(UUID shipId, ResourceKind resourceKind, int qty) {
        this.shipId = shipId;
        this.resourceKind = resourceKind;
        this.qty = qty;
    }

    public void incrementBy(int units) {
        this.qty += units;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }
}
