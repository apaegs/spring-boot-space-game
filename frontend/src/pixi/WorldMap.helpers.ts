/**
 * Pure, side-effect-free helpers extracted from {@link WorldMap} so they can
 * be unit-tested without spinning up a Pixi {@code Application}. WorldMap re-
 * exports nothing here — it consumes these directly.
 *
 * Nothing in this file may import from `pixi.js` or any module that does, or
 * tests would have to fake the GPU. Keep it numbers-only.
 */

/**
 * Width and height (in CSS pixels) of the world canvas. WorldMap's coordinate
 * math depends on it; exported so tests can pass the same value WorldMap uses.
 */
export const CANVAS_PX = 600

/** Grid is square: 100 × 100 tiles. */
export const GRID_CELLS = 100

/** Base size of one tile at zoom 1.0. CANVAS_PX / GRID_CELLS = 6. */
export const TILE_PX = 6

/**
 * Tile center in world pixels. {@code +0.5} puts the marker in the middle of
 * its cell rather than the upper-left corner.
 */
export function tileToPx(x: number, y: number, tilePx: number = TILE_PX): { px: number; py: number } {
    return {
        px: (x + 0.5) * tilePx,
        py: (y + 0.5) * tilePx,
    }
}

/** Clamp {@code value} into the inclusive {@code [min, max]} range. */
export function clamp(value: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, value))
}

/**
 * Convert a screen-space drag delta (in CSS pixels) into a camera-space
 * delta (in tile coordinates) at the given zoom. Sign is negated: dragging
 * right physically "pulls" the world right, which means the camera moves
 * left to keep the same world point under the cursor.
 */
export function pixelsToCameraDelta(
    dx: number,
    dy: number,
    zoom: number,
    tilePx: number = TILE_PX,
): { dx: number; dy: number } {
    const scale = tilePx * zoom
    // `+ 0` collapses the IEEE-754 -0 that `-(0) / n` produces back to +0
    // so callers (and strict-equality tests) don't have to special-case it.
    return { dx: -dx / scale + 0, dy: -dy / scale + 0 }
}
