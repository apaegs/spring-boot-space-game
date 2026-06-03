package org.example.springbootspacegame.body;

import org.example.springbootspacegame.resource.ResourceKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link BodyBuyPrice} ({@code body_id} + {@code resource_kind}).
 * Required by JPA's {@code @IdClass} mapping.
 */
public class BodyBuyPriceId implements Serializable {

    private UUID bodyId;
    private ResourceKind resourceKind;

    public BodyBuyPriceId() {
        // for JPA
    }

    public BodyBuyPriceId(UUID bodyId, ResourceKind resourceKind) {
        this.bodyId = bodyId;
        this.resourceKind = resourceKind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BodyBuyPriceId other)) return false;
        return Objects.equals(bodyId, other.bodyId)
                && resourceKind == other.resourceKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bodyId, resourceKind);
    }
}
