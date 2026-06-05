import { describe, expect, it } from 'vitest'
import { CANVAS_PX, GRID_CELLS, TILE_PX, clamp, pixelsToCameraDelta, tileToPx } from './WorldMap.helpers'

describe('WorldMap.helpers', () => {
    describe('grid constants', () => {
        // Anchor invariant: the 600 px canvas is exactly the world. If anyone
        // changes one constant without the others, every coordinate breaks
        // silently — this test surfaces that instead.
        it('CANVAS_PX is GRID_CELLS * TILE_PX', () => {
            expect(CANVAS_PX).toBe(GRID_CELLS * TILE_PX)
        })
    })

    describe('tileToPx', () => {
        it('puts the marker in the middle of the cell, not the corner', () => {
            expect(tileToPx(0, 0)).toEqual({ px: 3, py: 3 })
        })

        it('scales linearly along x and y', () => {
            expect(tileToPx(1, 0)).toEqual({ px: 9, py: 3 })
            expect(tileToPx(0, 1)).toEqual({ px: 3, py: 9 })
            expect(tileToPx(10, 10)).toEqual({ px: 63, py: 63 })
        })

        it('honours a custom tilePx (zoomed world)', () => {
            expect(tileToPx(0, 0, 12)).toEqual({ px: 6, py: 6 })
            expect(tileToPx(5, 5, 12)).toEqual({ px: 66, py: 66 })
        })
    })

    describe('clamp', () => {
        it('returns the value when inside the range', () => {
            expect(clamp(5, 0, 10)).toBe(5)
        })

        it('clamps below the minimum', () => {
            expect(clamp(-3, 0, 10)).toBe(0)
        })

        it('clamps above the maximum', () => {
            expect(clamp(99, 0, 10)).toBe(10)
        })

        it('handles min === max', () => {
            expect(clamp(1, 5, 5)).toBe(5)
            expect(clamp(7, 5, 5)).toBe(5)
        })

        it('treats min and max as inclusive', () => {
            expect(clamp(0, 0, 10)).toBe(0)
            expect(clamp(10, 0, 10)).toBe(10)
        })
    })

    describe('pixelsToCameraDelta', () => {
        it('negates the sign so dragging right moves the camera left', () => {
            // 12 px right at zoom 1 == TILE_PX * 1 = 6 px per tile → 2 tiles
            // worth of screen movement → camera shifts left 2 tiles.
            expect(pixelsToCameraDelta(12, 0, 1)).toEqual({ dx: -2, dy: 0 })
            expect(pixelsToCameraDelta(0, 12, 1)).toEqual({ dx: 0, dy: -2 })
        })

        it('divides the delta by zoom so high-zoom drags pan less in world space', () => {
            // Same screen movement at zoom 4 covers 4x fewer world tiles.
            expect(pixelsToCameraDelta(24, 0, 4)).toEqual({ dx: -1, dy: 0 })
        })

        it('honours a custom tilePx', () => {
            expect(pixelsToCameraDelta(24, 0, 1, 12)).toEqual({ dx: -2, dy: 0 })
        })
    })
})
