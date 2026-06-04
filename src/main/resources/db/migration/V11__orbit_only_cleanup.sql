-- Issue #87 (orbit-only model) cleanup. Two changes:
--
-- 1. Drop the auto_inserted column on ship_orders. The auto-prerequisite
--    middleware that set it to TRUE is gone (LAND/TAKE_OFF orders no longer
--    exist), so every row would otherwise just hold DEFAULT FALSE forever.
--    Dropping the column is preferable to leaving dead state.
--
-- 2. Bump any ship currently sitting on a celestial body's tile to one tile
--    east. In the orbit-only model ships never share a tile with a body —
--    spawn is at (51,50) adjacent to Earth, and historical LAND-on-tile state
--    is incompatible with the new derivation rule. Defensive: covers
--    test-leftover rows and any hand-run dev DB. Safe-by-construction:
--    no V9 seed body has x = 99, so x+1 stays inside the 0..99 grid. Tile
--    collisions with other ships at (x+1, y) are tolerated until the
--    per-tile collision check (#88) lands.

ALTER TABLE ship_orders DROP COLUMN auto_inserted;

UPDATE ships
   SET x = x + 1
 WHERE (x, y) IN (SELECT x, y FROM celestial_bodies);
