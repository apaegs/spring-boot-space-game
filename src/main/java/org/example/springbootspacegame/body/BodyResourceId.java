package org.example.springbootspacegame.body;

import org.example.springbootspacegame.resource.ResourceKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link BodyResource} ({@code body_id} + {@code resource_kind}).
 * Required by JPA's {@code @IdClass} mapping; not used by application code.
 */
public class BodyResourceId implements Serializable {

    private UUID bodyId;
    private ResourceKind resourceKind;

    public BodyResourceId() {
        // for JPA
    }

    public BodyResourceId(UUID bodyId, ResourceKind resourceKind) {
        this.bodyId = bodyId;
        this.resourceKind = resourceKind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BodyResourceId other)) return false;
        return Objects.equals(bodyId, other.bodyId)
                && resourceKind == other.resourceKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bodyId, resourceKind);
    }
}
