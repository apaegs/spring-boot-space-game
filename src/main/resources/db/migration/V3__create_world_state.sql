-- Singleton table holding the world's shared state. There is exactly one row
-- (id = 1, enforced by the CHECK constraint) so any service can SELECT/UPDATE
-- without thinking about which row to touch.
--
-- grid_width/height are stored here even though they're effectively constants in
-- v1 — pulling them from the DB lets future config changes happen without a code
-- deploy, and lets us add per-instance world sizes later without renaming columns.
CREATE TABLE world_state (
    id            SMALLINT     PRIMARY KEY CHECK (id = 1),
    current_tick  BIGINT       NOT NULL DEFAULT 0,
    last_tick_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    grid_width    INT          NOT NULL DEFAULT 100,
    grid_height   INT          NOT NULL DEFAULT 100
);

-- Seed the singleton row so app code never has to handle the "table is empty" case.
INSERT INTO world_state (id) VALUES (1);
