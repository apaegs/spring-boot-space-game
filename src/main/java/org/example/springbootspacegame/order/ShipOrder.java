package org.example.springbootspacegame.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single instruction in a ship's order queue. See DOMAIN.md "ShipOrder".
 *
 * <p>{@code params} is a free-form JSONB blob whose shape depends on
 * {@link #kind}: MOVE has {@code {x, y}}, LAND is empty, future kinds will
 * define their own. The matching {@link OrderHandler} strategy unwraps it.
 */
@Entity
@Table(name = "ship_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ShipOrder {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "ship_id", nullable = false, updatable = false)
    private UUID shipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private OrderKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * Counter incremented by multi-tick handlers (EXTRACT in mode={ticks: N})
     * once per tick they make progress. Added in V10.
     */
    @Column(name = "progress_ticks", nullable = false)
    private int progressTicks;

    /**
     * Was this order created by the auto-prerequisite middleware (LAND before
     * EXTRACT/SELL, TAKE_OFF before MOVE), or by the player directly? Drives
     * the "↩ auto" badge in the frontend (PR 3). Added in V10.
     */
    @Column(name = "auto_inserted", nullable = false)
    private boolean autoInserted;

    public ShipOrder(UUID shipId, OrderKind kind, Map<String, Object> params) {
        this(shipId, kind, params, false);
    }

    /**
     * Auto-inserted constructor: used by {@code ShipOrderService.appendOrder}
     * when the queue middleware injects a prerequisite (e.g. LAND before
     * EXTRACT) on the player's behalf.
     */
    public ShipOrder(UUID shipId, OrderKind kind, Map<String, Object> params, boolean autoInserted) {
        this.shipId = shipId;
        this.kind = kind;
        // Defensive copy so callers can't mutate the entity's state from outside.
        this.params = params == null ? new HashMap<>() : new HashMap<>(params);
        this.status = OrderStatus.PENDING;
        this.autoInserted = autoInserted;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        if (params == null) {
            params = new HashMap<>();
        }
    }

    /**
     * Explicitly set {@link #createdAt} before persist. Used by the
     * auto-prerequisite middleware to give an auto-inserted LAND/TAKE_OFF a
     * timestamp slightly earlier than the main order, so the queue's
     * {@code ORDER BY created_at} ordering puts the prerequisite first even
     * when both rows are saved in the same millisecond.
     *
     * <p>Must be called before {@code save()} — {@link #prePersist} will not
     * overwrite a non-null {@code createdAt}.
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void markActive() {
        this.status = OrderStatus.ACTIVE;
        this.startedAt = OffsetDateTime.now();
    }

    public void markCompleted() {
        this.status = OrderStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
        this.completedAt = OffsetDateTime.now();
    }

    /** Increment {@link #progressTicks} by one. Used by multi-tick handlers. */
    public void incrementProgressTicks() {
        this.progressTicks++;
    }
}
