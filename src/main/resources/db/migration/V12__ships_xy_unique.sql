-- Issue #88 (per-tile collision) — defense-in-depth at the schema layer.
--
-- Goal: enforce "at most one ship per tile". Backstops the application-level
-- check in MoveOrderHandler.validateForShip / spawn-spiral so a concurrent
-- race (two registrations both reading "(51,50) is free" then both inserting)
-- can't slip a duplicate through.
--
-- Step 1: precheck. Each duplicate row (row_number > 1 inside its (x,y)
-- partition) gets a proposed new tile via a simple deterministic shift:
-- new_x = LEAST(GREATEST(x + rn - 1, 0), 99). With realistic post-V11 data
-- (a handful of test ships stacked on (51,50)) the shifts produce
-- (52,50), (53,50), … none of which collide with bodies or other ships
-- between (52,50) and (94,50). For any other layout (multiple distinct
-- stacks, a stack near the eastern edge, a stack whose shift target hits
-- a body or another ship) the precheck DO-block aborts with a clear
-- error and the migration leaves the schema untouched. A human resolves
-- by hand and re-runs.
--
-- Step 2: apply the same shift to the duplicates and add the constraint.
-- Application code translates DataIntegrityViolationException matching
-- "ships_xy_unique" into a spawn-spiral retry (ShipService.spawnShip).

DO $$
DECLARE
    duplicate_count int;
BEGIN
    -- Project the post-shift world: keepers (rn=1) stay put, duplicates
    -- get the deterministic shift. Add celestial_bodies. Count tiles that
    -- would still hold more than one entity — those are the collisions
    -- the naive shift would leave behind.
    WITH proposed_positions AS (
        SELECT id,
               CASE WHEN ROW_NUMBER() OVER (PARTITION BY x, y ORDER BY created_at) = 1
                    THEN x
                    ELSE LEAST(GREATEST(x + ROW_NUMBER() OVER (PARTITION BY x, y ORDER BY created_at) - 1, 0), 99)
               END AS new_x,
               y AS new_y
          FROM ships
    ),
    all_occupants AS (
        SELECT new_x AS x, new_y AS y FROM proposed_positions
        UNION ALL
        SELECT x, y FROM celestial_bodies
    )
    SELECT COUNT(*) - COUNT(DISTINCT (x, y))
      INTO duplicate_count
      FROM all_occupants;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION
            'V12 dedup precheck: % proposed ship position(s) would still collide with another ship or a celestial body after the deterministic shift. Manual cleanup required before re-running.',
            duplicate_count;
    END IF;
END $$;

UPDATE ships
   SET x = LEAST(GREATEST(ships.x + r.rn - 1, 0), 99)
  FROM (
      SELECT id, ROW_NUMBER() OVER (PARTITION BY x, y ORDER BY created_at) AS rn
        FROM ships
  ) r
 WHERE ships.id = r.id AND r.rn > 1;

ALTER TABLE ships
  ADD CONSTRAINT ships_xy_unique UNIQUE (x, y);
