-- Foundation for the resource system (design: issue #46, v3 comment).
--
-- This migration is structural only — no new gameplay behavior lands here.
-- PR 2 adds the handlers (EXTRACT, SELL, TAKE_OFF) that actually USE these
-- tables; this migration just makes the model available.
--
-- Six changes, in order:
--   1. planets   → celestial_bodies (+ kind column)
--   2. body_resources       (per-body reserves of a given resource_kind)
--   3. body_buy_prices      (per-body buy prices for a given resource_kind)
--   4. users.credits        (currency on the user, not the ship)
--   5. ship_types           (stats per type; seeded with MOTHERSHIP)
--   6. ships.ship_type_id   (FK to ship_types; backfilled to MOTHERSHIP)
--   7. ship_cargo           (per-ship per-resource cargo)
--
-- All resource_kind / body kind values are VARCHAR strings, not enum types in
-- Postgres — the application-layer ResourceKind / CelestialBodyKind enums are
-- the source of truth. Adding a new kind = enum value + data rows; no schema
-- migration needed.

-- 1. Body taxonomy. Existing rows become rocky planets (the safe default for
--    the v1 hand-seeded planets — the V9 seed will REPLACE the row set with a
--    varied mix, but the column needs a default during the rename window in
--    case anyone has dev data they want to keep around.)
ALTER TABLE planets RENAME TO celestial_bodies;
ALTER TABLE celestial_bodies RENAME CONSTRAINT planets_xy_unique TO celestial_bodies_xy_unique;
ALTER TABLE celestial_bodies ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'ROCKY_PLANET';
ALTER TABLE celestial_bodies ALTER COLUMN kind DROP DEFAULT;

-- 2. Per-body reserves. Absence of a row = body doesn't have that resource at
--    all (rather than a row with reserve=0, which means "depleted but used to
--    have"). Both states are useful for UI distinction later.
CREATE TABLE body_resources (
    body_id        UUID         NOT NULL REFERENCES celestial_bodies(id) ON DELETE CASCADE,
    resource_kind  VARCHAR(16)  NOT NULL,
    reserve        INT          NOT NULL CHECK (reserve >= 0),
    PRIMARY KEY (body_id, resource_kind)
);

-- 3. Per-body buy prices. Absence of a row = body doesn't buy this resource.
--    NULL would conflate "no row" with "row, NULL price" — separate rows are
--    cleaner.
CREATE TABLE body_buy_prices (
    body_id        UUID         NOT NULL REFERENCES celestial_bodies(id) ON DELETE CASCADE,
    resource_kind  VARCHAR(16)  NOT NULL,
    price_per_unit INT          NOT NULL CHECK (price_per_unit > 0),
    PRIMARY KEY (body_id, resource_kind)
);

-- 4. Currency on the user. BIGINT because credits accumulate over a campaign
--    and a low INT cap (2.1 billion) is uncomfortably close for a long-running
--    economy. Default 0 = fresh accounts start broke.
ALTER TABLE users ADD COLUMN credits BIGINT NOT NULL DEFAULT 0;

-- 5. Ship types catalog. Data-driven: adding a new type = INSERT a row.
--    The application doesn't care how many rows are here at compile time.
CREATE TABLE ship_types (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(32)  UNIQUE NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    cargo_capacity  INT          NOT NULL CHECK (cargo_capacity > 0),
    extract_rate    INT          NOT NULL CHECK (extract_rate > 0),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seed the v1 ship type with a deterministic UUID so the ships.ship_type_id
-- backfill below can reference it without a subquery.
INSERT INTO ship_types (id, code, name, cargo_capacity, extract_rate) VALUES
    ('00000000-0000-0000-0000-000000000001', 'MOTHERSHIP', 'Mothership', 500, 10);

-- 6. ship_type_id on the ship. Backfill existing rows via DEFAULT (so the NOT
--    NULL constraint is satisfied during the ALTER), then drop the default
--    so future inserts must specify the type explicitly.
ALTER TABLE ships ADD COLUMN ship_type_id UUID NOT NULL
    DEFAULT '00000000-0000-0000-0000-000000000001'
    REFERENCES ship_types(id);
ALTER TABLE ships ALTER COLUMN ship_type_id DROP DEFAULT;

-- 7. Per-ship per-resource cargo. Composite PK keeps "this ship's IRON" a
--    single index seek. Cascade from ships handles deletion (already wired
--    in V2 from users → ships → ship_orders; ship_cargo joins that chain).
CREATE TABLE ship_cargo (
    ship_id        UUID         NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    resource_kind  VARCHAR(16)  NOT NULL,
    qty            INT          NOT NULL CHECK (qty >= 0),
    PRIMARY KEY (ship_id, resource_kind)
);
