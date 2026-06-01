package org.example.springbootspacegame.ship;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameShipRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 64, message = "Name must be 64 characters or fewer")
        String name
) {}
