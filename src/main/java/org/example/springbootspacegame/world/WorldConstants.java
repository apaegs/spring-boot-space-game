package org.example.springbootspacegame.world;

/**
 * Game-wide constants for the shared world. Single source of truth for values
 * that used to live in multiple places (entity columns, validation literals,
 * Javadoc, SQL CHECK constraints).
 *
 * <p>v1 fixes the grid at 100×100 forever — see issue #29. If per-instance grid
 * sizing ever becomes a real requirement, the path is to move {@link #GRID_SIZE}
 * back onto {@code WorldState} and have validators read it at runtime; the schema
 * CHECK constraints would have to be replaced with triggers at the same time.
 */
public final class WorldConstants {

    /** Width and height of the square play grid. Valid tile coordinates are {@code 0..GRID_SIZE - 1}. */
    public static final int GRID_SIZE = 100;

    private WorldConstants() {
    }
}
