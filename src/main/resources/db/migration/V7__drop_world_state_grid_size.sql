-- Drop grid_width / grid_height from world_state. Per issue #29, the grid is
-- fixed at 100x100 forever in v1; the single source of truth now lives in
-- WorldConstants.GRID_SIZE. The CHECK constraints on ships(x,y) and planets(x,y)
-- (literal < 100) document the same constant at the schema layer.
ALTER TABLE world_state
    DROP COLUMN grid_width,
    DROP COLUMN grid_height;
