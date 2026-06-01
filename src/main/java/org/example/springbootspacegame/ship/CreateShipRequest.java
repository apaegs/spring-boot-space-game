package org.example.springbootspacegame.ship;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/ships}. All fields optional — players can
 * accept the auto-generated defaults by sending an empty body, or override the
 * name (and later, spawn coords / ship type) if they want to.
 *
 * <p>Same pattern is the door for player-chosen names in future: just start
 * sending {@code name} from the UI.
 */
public record CreateShipRequest(

        @Size(min = 1, max = 64)
        @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "must not contain control characters")
        String name
) {
    public CreateShipRequest {
        // Allow null name (auto-generated). Trim whitespace if provided so
        // "  Falcon  " doesn't pollute the DB.
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = null;
        }
    }
}
