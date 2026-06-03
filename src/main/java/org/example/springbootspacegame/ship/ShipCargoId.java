package org.example.springbootspacegame.ship;

import org.example.springbootspacegame.resource.ResourceKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link ShipCargo} ({@code ship_id} + {@code resource_kind}).
 * Required by JPA's {@code @IdClass} mapping.
 */
public class ShipCargoId implements Serializable {

    private UUID shipId;
    private ResourceKind resourceKind;

    public ShipCargoId() {
        // for JPA
    }

    public ShipCargoId(UUID shipId, ResourceKind resourceKind) {
        this.shipId = shipId;
        this.resourceKind = resourceKind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShipCargoId other)) return false;
        return Objects.equals(shipId, other.shipId)
                && resourceKind == other.resourceKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shipId, resourceKind);
    }
}
