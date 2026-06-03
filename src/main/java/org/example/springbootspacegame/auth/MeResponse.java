package org.example.springbootspacegame.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String username,
        String email,
        long credits,
        OffsetDateTime createdAt
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCredits(),
                user.getCreatedAt()
        );
    }
}
