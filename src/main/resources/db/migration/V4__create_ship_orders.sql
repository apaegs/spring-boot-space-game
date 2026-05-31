-- The ship's order queue. Players append orders (MOVE, LAND, ...) via the API;
-- the tick processor pulls the oldest unfinished order per ship each tick and
-- advances it one step.
--
-- params is JSONB so new order kinds (WAIT/SCAN/MINE/...) don't need a migration
-- per type — each kind defines its own payload shape, validated in the matching
-- OrderHandler strategy class. See DOMAIN.md "ShipOrder".
CREATE TABLE ship_orders (
    id            UUID         PRIMARY KEY,
    ship_id       UUID         NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    kind          VARCHAR(32)  NOT NULL,
    params        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ
);

-- Partial index so "find this ship's next order to process" is a single index seek
-- regardless of how much completed history piles up. created_at ASC = FIFO queue.
CREATE INDEX ship_orders_active_idx
    ON ship_orders (ship_id, created_at)
    WHERE status IN ('PENDING', 'ACTIVE');

-- The orders queue is now the source of truth for "what's the ship doing".
-- Drop the now-redundant destination columns on ships. The composite CHECK
-- has to go first since DROP COLUMN doesn't cascade across multi-column
-- constraints.
ALTER TABLE ships DROP CONSTRAINT ships_destination_both_or_neither;
ALTER TABLE ships DROP COLUMN destination_x;
ALTER TABLE ships DROP COLUMN destination_y;
