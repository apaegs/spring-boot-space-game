-- Ship: the player's mothership. v1 = exactly one per user, enforced in app logic
-- (no UNIQUE on user_id) so future fleet support is a code change, not a migration.
-- See DOMAIN.md "Forward-compat".
CREATE TABLE ships (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(64)  NOT NULL,
    x               INT          NOT NULL CHECK (x >= 0 AND x < 100),
    y               INT          NOT NULL CHECK (y >= 0 AND y < 100),
    -- Both destination_x and destination_y are set together or not at all.
    -- Enforced via the CHECK below; the application also nulls both on arrival.
    destination_x   INT          CHECK (destination_x IS NULL OR (destination_x >= 0 AND destination_x < 100)),
    destination_y   INT          CHECK (destination_y IS NULL OR (destination_y >= 0 AND destination_y < 100)),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ships_destination_both_or_neither
        CHECK ((destination_x IS NULL AND destination_y IS NULL)
            OR (destination_x IS NOT NULL AND destination_y IS NOT NULL))
);

-- Fast lookup of "this user's ship(s)" — primary access pattern in v1.
CREATE INDEX ships_user_id_idx ON ships (user_id);
