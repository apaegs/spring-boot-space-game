package org.example.springbootspacegame.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Authentication identity. See DOMAIN.md.
 *
 * <p>Intentionally not annotated with {@code @Data}: equals/hashCode on JPA entities is a trap
 * (identity changes across detach/attach cycles).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class User {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    /**
     * BCrypt hash of the password. Getter is package-private so the hash never leaks
     * outside the {@code auth} package (e.g. via accidental DTO serialization).
     */
    @Getter(AccessLevel.PACKAGE)
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * In-game currency. Earned by the SELL order handler (PR 2). Starting
     * balance is 0; the DB DEFAULT in V8 backfills existing rows.
     */
    @Column(nullable = false)
    private long credits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * Add {@code amount} credits to the user's balance. Called by the SELL
     * order handler (PR 2). Caller must validate {@code amount >= 0} —
     * negative deltas would silently grant credits.
     */
    public void addCredits(long amount) {
        this.credits += amount;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
