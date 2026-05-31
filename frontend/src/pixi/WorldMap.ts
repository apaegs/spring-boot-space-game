import { Application, Container, Graphics, Text, TextStyle } from 'pixi.js'
import type { PlanetDto, ShipDto } from '../types/api'

/**
 * PixiJS world map. Lives in {@code src/pixi/} per CLAUDE.md — React doesn't
 * reach in here, and this module doesn't reach into React. React talks to it
 * exclusively via the public methods below.
 *
 * v1 layout decision: the whole 100×100 grid fits in a fixed canvas. No
 * pan / zoom yet. The grid is small enough to read at a glance, and skipping
 * the camera lets the map land in one focused commit. Pan/zoom is a follow-up.
 */
export class WorldMap {
    private static readonly CANVAS_PX = 600
    private static readonly GRID_CELLS = 100
    private static readonly TILE_PX = WorldMap.CANVAS_PX / WorldMap.GRID_CELLS // 6

    private static readonly COLORS = {
        background: 0x0a0a1a,
        gridLine: 0x1a1a2e,
        planet: 0xffaa00,
        planetLabel: 0xffffff,
        ship: 0x66ddff,
        shipOutline: 0xffffff,
    }

    private app: Application | null = null
    private planetsLayer: Container | null = null
    private shipLayer: Container | null = null
    private onPlanetClick: ((planet: PlanetDto) => void) | null = null

    /**
     * Mount the Pixi app inside the given container. Idempotent-ish: calling
     * twice without {@link destroy} between will leak the first app, so the
     * React side guards that with the unmount cleanup.
     */
    async init(parent: HTMLDivElement): Promise<void> {
        const app = new Application()
        await app.init({
            width: WorldMap.CANVAS_PX,
            height: WorldMap.CANVAS_PX,
            background: WorldMap.COLORS.background,
            antialias: true,
        })
        parent.appendChild(app.canvas)
        this.app = app

        this.drawGrid()

        this.planetsLayer = new Container()
        this.shipLayer = new Container()
        app.stage.addChild(this.planetsLayer)
        app.stage.addChild(this.shipLayer)
    }

    /**
     * Tear down the Pixi app. Must be called when the React component
     * unmounts or React would leak a renderer + canvas every remount.
     */
    destroy(): void {
        if (this.app) {
            this.app.destroy(true, { children: true, texture: true })
            this.app = null
            this.planetsLayer = null
            this.shipLayer = null
        }
    }

    setPlanets(planets: PlanetDto[]): void {
        if (!this.planetsLayer) return
        this.planetsLayer.removeChildren()

        for (const planet of planets) {
            const { px, py } = this.tileToPx(planet.x, planet.y)

            const dot = new Graphics()
            // Hit area is the larger circle (10px); the visible disc stays at
            // 6px. Makes clicking less fiddly on a 6px-tile grid without
            // visually inflating the planet.
            dot.circle(px, py, 10)
            dot.fill({ color: WorldMap.COLORS.planet, alpha: 0 }) // invisible hit area
            dot.circle(px, py, 6)
            dot.fill(WorldMap.COLORS.planet)
            dot.eventMode = 'static'
            dot.cursor = 'pointer'
            dot.on('pointerdown', () => this.onPlanetClick?.(planet))
            this.planetsLayer.addChild(dot)

            const label = new Text({
                text: planet.name,
                style: new TextStyle({
                    fontFamily: 'system-ui, sans-serif',
                    fontSize: 10,
                    fill: WorldMap.COLORS.planetLabel,
                }),
            })
            label.x = px + 8
            label.y = py - 6
            this.planetsLayer.addChild(label)
        }
    }

    /**
     * Set the callback invoked when a planet is clicked. The map calls this
     * with the {@link PlanetDto} the player clicked. React typically wires it
     * to a "queue MOVE + LAND to this planet" mutation.
     */
    setOnPlanetClick(callback: ((planet: PlanetDto) => void) | null): void {
        this.onPlanetClick = callback
    }

    setShip(ship: ShipDto | null): void {
        if (!this.shipLayer) return
        this.shipLayer.removeChildren()
        if (!ship) return

        const { px, py } = this.tileToPx(ship.x, ship.y)
        const marker = new Graphics()
        marker.poly([px, py - 6, px + 5, py + 5, px - 5, py + 5])
        marker.fill(WorldMap.COLORS.ship)
        marker.stroke({ color: WorldMap.COLORS.shipOutline, width: 1 })
        this.shipLayer.addChild(marker)
    }

    private drawGrid(): void {
        if (!this.app) return
        const grid = new Graphics()
        const total = WorldMap.CANVAS_PX

        for (let i = 0; i <= WorldMap.GRID_CELLS; i += 10) {
            const offset = i * WorldMap.TILE_PX
            grid.moveTo(offset, 0).lineTo(offset, total)
            grid.moveTo(0, offset).lineTo(total, offset)
        }
        grid.stroke({ color: WorldMap.COLORS.gridLine, width: 1 })
        this.app.stage.addChild(grid)
    }

    private tileToPx(x: number, y: number): { px: number; py: number } {
        // Tile centers — +0.5 puts the marker in the middle of its cell.
        return {
            px: (x + 0.5) * WorldMap.TILE_PX,
            py: (y + 0.5) * WorldMap.TILE_PX,
        }
    }
}
