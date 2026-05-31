package org.example.springbootspacegame.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
                message = "may only contain letters, digits, underscore and hyphen")
        String username,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
    /**
     * BCrypt silently truncates passwords past 72 bytes (not chars). For ASCII the {@code @Size}
     * cap above is sufficient, but multi-byte UTF-8 (e.g. emoji, é) can push a 72-char string past
     * 72 bytes. Reject those at validation time so users aren't surprised that only a prefix of
     * their password actually matters.
     */
    @AssertTrue(message = "password must be at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
