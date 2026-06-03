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
 * Per-body reserve of a single resource. Composite PK {@code (body_id, resource_kind)}.
 *
 * <p>Absence of a row means the body lacks that resource at all — distinct from
 * a row with {@code reserve = 0}, which means "depleted but used to have". The
 * UI will differentiate these in PR 3.
 *
 * <p>Reserves are decremented by the EXTRACT handler (PR 2) within the tick
 * processor's transaction.
 */
@Entity
@Table(name = "body_resources")
@IdClass(BodyResourceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class BodyResource {

    @Id
    @Column(name = "body_id", nullable = false, updatable = false)
    private UUID bodyId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_kind", nullable = false, updatable = false)
    private ResourceKind resourceKind;

    @Column(nullable = false)
    private int reserve;

    public BodyResource(UUID bodyId, ResourceKind resourceKind, int reserve) {
        // Mirrors the V8 DB CHECK (reserve >= 0). A row with reserve = 0 is
        // valid — it means "depleted but used to have", distinct from no row
        // at all ("never had").
        if (reserve < 0) {
            throw new IllegalArgumentException("reserve must be >= 0, was " + reserve);
        }
        this.bodyId = bodyId;
        this.resourceKind = resourceKind;
        this.reserve = reserve;
    }

    /**
     * Decrement the reserve by {@code units}. Caller must clamp to the
     * available amount. The DB CHECK ({@code reserve >= 0}) enforces
     * non-negative at flush time; this guard rejects negative deltas at the
     * mutation point so a buggy caller can't silently mint reserves.
     */
    public void decrementBy(int units) {
        if (units < 0) {
            throw new IllegalArgumentException("units must be >= 0, was " + units);
        }
        this.reserve -= units;
    }
}
