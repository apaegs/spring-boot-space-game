-- Issue #88 (per-tile collision) — defense-in-depth at the schema layer.
--
-- Goal: enforce "at most one ship per tile". Backstops the application-level
-- check in MoveOrderHandler.validateForShip / spawn-spiral so a concurrent
-- race (two registrations both reading "(51,50) is free" then both inserting)
-- can't slip a duplicate through.
--
-- Step 1: deduplicate any existing rows so the constraint can be added.
-- The realistic state in dev / prod-light is "a handful of test ships
-- stacked on the post-V11 spawn tile (51,50)". For each tile with >1 ship,
-- the second oldest shifts to x+1, third to x+2, etc. Earth sits at (50,50)
-- and no V9 body lives between (52,50) and (94,50), so shifts are
-- collision-free for realistic stack depths.
--
-- This is a best-effort dedup. If a future DB ever has multiple distinct
-- stacks that collide after the shift (e.g. 5 ships at (51,50) AND 5 ships
-- at (52,50)), the ALTER below will fail and a human will resolve it by
-- hand. With current spawn logic that scenario can't arise.
--
-- Step 2: the constraint itself. Application code translates
-- DataIntegrityViolationException matching "ships_xy_unique" into a
-- spawn-spiral retry (ShipService.spawnShip).

UPDATE ships
   SET x = LEAST(GREATEST(ships.x + r.rn - 1, 0), 99)
  FROM (
      SELECT id, ROW_NUMBER() OVER (PARTITION BY x, y ORDER BY created_at) AS rn
        FROM ships
  ) r
 WHERE ships.id = r.id AND r.rn > 1;

ALTER TABLE ships
  ADD CONSTRAINT ships_xy_unique UNIQUE (x, y);
