-- Planets are pre-seeded, immutable in v1: places players can travel to and
-- LAND on. See DOMAIN.md "Planet".
CREATE TABLE planets (
    id           UUID         PRIMARY KEY,
    x            INT          NOT NULL CHECK (x >= 0 AND x < 100),
    y            INT          NOT NULL CHECK (y >= 0 AND y < 100),
    name         VARCHAR(64)  NOT NULL,
    description  TEXT,

    -- At most one planet per tile. The LAND handler relies on this — "is there
    -- a planet at (x, y)?" is a single index lookup.
    CONSTRAINT planets_xy_unique UNIQUE (x, y)
);

-- v1 seed: a small spread across the grid. Earth sits on the spawn tile so
-- new players start standing on their home planet (their first LAND order
-- works immediately, without needing to MOVE anywhere first).
INSERT INTO planets (id, x, y, name, description) VALUES
    (gen_random_uuid(), 50, 50, 'Earth',     'The blue homeworld. Where every player''s journey begins.'),
    (gen_random_uuid(), 60, 55, 'Mars',      'Rust-red and quiet. The first stop for most pilots.'),
    (gen_random_uuid(), 30, 70, 'Jupiter',   'Gas giant with a storm older than recorded history.'),
    (gen_random_uuid(), 70, 30, 'Saturn',    'Ringed and serene. A trading post hub in the making.'),
    (gen_random_uuid(), 10, 10, 'Proxima B', 'Tidally locked terrestrial. The nearest neighbour.'),
    (gen_random_uuid(), 90, 90, 'Sirius',    'A binary system. Bright, hot, and on the far edge of the map.');
