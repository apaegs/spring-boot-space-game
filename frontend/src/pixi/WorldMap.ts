import { Application, Container, FederatedPointerEvent, Graphics, Text, TextStyle } from 'pixi.js'
import type { PlanetDto, ShipDto } from '../types/api'

/**
 * PixiJS world map. Lives in {@code src/pixi/} per CLAUDE.md — React doesn't
 * reach in here, and this module doesn't reach into React. React talks to it
 * exclusively via the public methods below.
 *
 * <h3>Camera</h3>
 * The viewport is selection-driven. With no selected ship the camera shows the
 * whole 100×100 world (`ZOOM_OUT`). With a selected ship the camera centers on
 * it at `ZOOM_IN`, showing roughly 25×25 tiles around the ship. The transition
 * is a snap in v1; smooth interpolation lands as a follow-up — keeping the
 * implementation small while the rest of the UI churns.
 *
 * <h3>Tile clicks</h3>
 * The whole canvas is one big invisible hit area on the background layer. On
 * click, the screen coordinates are inverted through the camera transform to
 * a tile coordinate, and {@code onTileClick(x, y)} fires. Out-of-bounds clicks
 * are dropped silently.
 */
export class WorldMap {
    private static readonly CANVAS_PX = 600
    private static readonly GRID_CELLS = 100
    private static readonly TILE_PX = 6 // base size at zoom 1.0; world = 600px

    private static readonly ZOOM_OUT = 1.0
    private static readonly ZOOM_IN = 4.0

    private static readonly COLORS = {
        background: 0x0a0a1a,
        gridLineMajor: 0x252540,
        gridLineMinor: 0x12122a,
        planet: 0xffaa00,
        planetLabel: 0xffffff,
        ship: 0x66ddff,
        shipSelected: 0xfff066,
        shipOutline: 0xffffff,
        hover: 0x66ddff,
    }

    private app: Application | null = null

    // Camera state. The world layer is translated/scaled so that the tile at
    // (camera.x, camera.y) ends up at the canvas centre.
    private camera = { x: 50, y: 50, zoom: WorldMap.ZOOM_OUT }

    // Layers, all parented to worldLayer which carries the camera transform.
    private worldLayer: Container | null = null
    private gridLayer: Graphics | null = null
    private hoverLayer: Graphics | null = null
    private planetsLayer: Container | null = null
    private shipsLayer: Container | null = null

    // Cached so we can re-render when selection / zoom changes without the
    // caller having to keep passing the same arrays.
    private planets: PlanetDto[] = []
    private ships: ShipDto[] = []
    private selectedShipId: string | null = null
    private hoverTile: { x: number; y: number } | null = null

    private onTileClick: ((x: number, y: number) => void) | null = null
    private onPlanetClick: ((planet: PlanetDto) => void) | null = null

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

        // Background hit area — invisible rectangle covering the canvas so
        // pointer events fire even outside any sprite. Bound to the stage so
        // its coordinates are in screen space, not world space.
        const hit = new Graphics()
        hit.rect(0, 0, WorldMap.CANVAS_PX, WorldMap.CANVAS_PX)
        hit.fill({ color: 0x000000, alpha: 0 })
        hit.eventMode = 'static'
        hit.on('pointerdown', (e: FederatedPointerEvent) => this.handleBackgroundClick(e))
        hit.on('pointermove', (e: FederatedPointerEvent) => this.handleHover(e))
        hit.on('pointerout', () => this.clearHover())
        app.stage.addChild(hit)

        // World layer holds everything that moves/zooms with the camera.
        this.worldLayer = new Container()
        app.stage.addChild(this.worldLayer)

        // Grid first (bottom), hover next so it sits over the grid but under
        // planets and ships — keeps the highlight from covering a planet's
        // label or a ship's marker when the cursor lands on it.
        this.gridLayer = new Graphics()
        this.hoverLayer = new Graphics()
        this.planetsLayer = new Container()
        this.shipsLayer = new Container()
        this.worldLayer.addChild(this.gridLayer)
        this.worldLayer.addChild(this.hoverLayer)
        this.worldLayer.addChild(this.planetsLayer)
        this.worldLayer.addChild(this.shipsLayer)

        this.applyCamera()
        this.drawGrid()
    }

    destroy(): void {
        if (this.app) {
            this.app.destroy(true, { children: true, texture: true })
            this.app = null
            this.worldLayer = null
            this.gridLayer = null
            this.hoverLayer = null
            this.planetsLayer = null
            this.shipsLayer = null
        }
    }

    setPlanets(planets: PlanetDto[]): void {
        this.planets = planets
        this.renderPlanets()
    }

    setShips(ships: ShipDto[], selectedShipId: string | null): void {
        this.ships = ships
        this.selectedShipId = selectedShipId
        this.updateCameraForSelection()
        this.renderShips()
    }

    setOnTileClick(callback: ((x: number, y: number) => void) | null): void {
        this.onTileClick = callback
    }

    setOnPlanetClick(callback: ((planet: PlanetDto) => void) | null): void {
        this.onPlanetClick = callback
    }

    // ---------- camera ----------

    private updateCameraForSelection(): void {
        if (this.selectedShipId === null) {
            this.camera = { x: 50, y: 50, zoom: WorldMap.ZOOM_OUT }
        } else {
            const ship = this.ships.find((s) => s.id === this.selectedShipId)
            if (ship) {
                this.camera = { x: ship.x, y: ship.y, zoom: WorldMap.ZOOM_IN }
            }
        }
        this.applyCamera()
        // Grid density depends on zoom; redraw so per-tile lines appear/vanish.
        this.drawGrid()
    }

    private applyCamera(): void {
        if (!this.worldLayer) return
        const halfCanvas = WorldMap.CANVAS_PX / 2
        const cameraPx = this.camera.x * WorldMap.TILE_PX
        const cameraPy = this.camera.y * WorldMap.TILE_PX
        this.worldLayer.scale.set(this.camera.zoom)
        this.worldLayer.position.set(
            halfCanvas - cameraPx * this.camera.zoom,
            halfCanvas - cameraPy * this.camera.zoom
        )
    }

    // ---------- rendering ----------

    private drawGrid(): void {
        if (!this.gridLayer) return
        this.gridLayer.clear()
        const total = WorldMap.CANVAS_PX

        // Per-tile grid: only meaningful when zoomed in enough that single
        // tiles are large enough to read. Drawn first so the major lines layer
        // on top.
        if (this.camera.zoom >= 2.0) {
            for (let i = 0; i <= WorldMap.GRID_CELLS; i++) {
                const offset = i * WorldMap.TILE_PX
                this.gridLayer.moveTo(offset, 0).lineTo(offset, total)
                this.gridLayer.moveTo(0, offset).lineTo(total, offset)
            }
            this.gridLayer.stroke({ color: WorldMap.COLORS.gridLineMinor, width: 0.5 })
        }

        // Major grid every 10 tiles, always visible — gives the player a
        // sense of scale even at world zoom.
        for (let i = 0; i <= WorldMap.GRID_CELLS; i += 10) {
            const offset = i * WorldMap.TILE_PX
            this.gridLayer.moveTo(offset, 0).lineTo(offset, total)
            this.gridLayer.moveTo(0, offset).lineTo(total, offset)
        }
        this.gridLayer.stroke({ color: WorldMap.COLORS.gridLineMajor, width: 1 })
    }

    private renderPlanets(): void {
        if (!this.planetsLayer) return
        this.planetsLayer.removeChildren()

        for (const planet of this.planets) {
            const { px, py } = WorldMap.tileToPx(planet.x, planet.y)

            const dot = new Graphics()
            // Larger transparent hit area on top of the visible disc — keeps
            // clicks easy at low zoom levels.
            dot.circle(px, py, 10)
            dot.fill({ color: WorldMap.COLORS.planet, alpha: 0 })
            dot.circle(px, py, 4)
            dot.fill(WorldMap.COLORS.planet)
            dot.eventMode = 'static'
            dot.cursor = 'pointer'
            dot.on('pointerdown', (e: FederatedPointerEvent) => {
                e.stopPropagation() // don't fall through to the tile click
                this.onPlanetClick?.(planet)
            })
            this.planetsLayer.addChild(dot)

            const label = new Text({
                text: planet.name,
                style: new TextStyle({
                    fontFamily: 'system-ui, sans-serif',
                    fontSize: 9,
                    fill: WorldMap.COLORS.planetLabel,
                }),
            })
            label.x = px + 6
            label.y = py - 5
            this.planetsLayer.addChild(label)
        }
    }

    private renderShips(): void {
        if (!this.shipsLayer) return
        this.shipsLayer.removeChildren()

        for (const ship of this.ships) {
            const isSelected = ship.id === this.selectedShipId
            const { px, py } = WorldMap.tileToPx(ship.x, ship.y)

            const marker = new Graphics()
            // Triangle pointing up. Selected ship gets a slightly larger / brighter
            // marker so it's distinguishable in a crowded tile.
            const size = isSelected ? 4 : 3
            marker.poly([px, py - size, px + size, py + size, px - size, py + size])
            marker.fill(isSelected ? WorldMap.COLORS.shipSelected : WorldMap.COLORS.ship)
            marker.stroke({ color: WorldMap.COLORS.shipOutline, width: 1 })
            this.shipsLayer.addChild(marker)
        }
    }

    // ---------- input ----------

    private handleBackgroundClick(event: FederatedPointerEvent): void {
        if (!this.onTileClick) return
        const tile = this.screenToTile(event.global.x, event.global.y)
        if (tile) this.onTileClick(tile.x, tile.y)
    }

    private handleHover(event: FederatedPointerEvent): void {
        const tile = this.screenToTile(event.global.x, event.global.y)
        if (tile === null) {
            this.clearHover()
            return
        }
        if (this.hoverTile?.x === tile.x && this.hoverTile?.y === tile.y) {
            return // unchanged — skip the redraw
        }
        this.hoverTile = tile
        this.renderHover()
    }

    private clearHover(): void {
        if (this.hoverTile === null) return
        this.hoverTile = null
        this.renderHover()
    }

    private renderHover(): void {
        if (!this.hoverLayer) return
        this.hoverLayer.clear()
        if (!this.hoverTile) return

        // Highlight covers the whole tile cell. The tile-center math we use
        // elsewhere needs adjusting back to the corner for a rect.
        const x = this.hoverTile.x * WorldMap.TILE_PX
        const y = this.hoverTile.y * WorldMap.TILE_PX
        this.hoverLayer.rect(x, y, WorldMap.TILE_PX, WorldMap.TILE_PX)
        this.hoverLayer.fill({ color: WorldMap.COLORS.hover, alpha: 0.18 })
        this.hoverLayer.stroke({ color: WorldMap.COLORS.hover, alpha: 0.6, width: 0.5 })
    }

    /**
     * Invert the camera transform: screen → world pixels → tile. Returns
     * null if the pointer is outside the 100×100 grid (which happens at zoom
     * 1.0 mostly never, but at zoom 4.0 quite a lot — the canvas extends
     * past world edges).
     */
    private screenToTile(screenX: number, screenY: number): { x: number; y: number } | null {
        const halfCanvas = WorldMap.CANVAS_PX / 2
        const worldPx = (screenX - halfCanvas) / this.camera.zoom + this.camera.x * WorldMap.TILE_PX
        const worldPy = (screenY - halfCanvas) / this.camera.zoom + this.camera.y * WorldMap.TILE_PX
        const tileX = Math.floor(worldPx / WorldMap.TILE_PX)
        const tileY = Math.floor(worldPy / WorldMap.TILE_PX)
        if (tileX < 0 || tileX >= WorldMap.GRID_CELLS) return null
        if (tileY < 0 || tileY >= WorldMap.GRID_CELLS) return null
        return { x: tileX, y: tileY }
    }

    private static tileToPx(x: number, y: number): { px: number; py: number } {
        // Tile centers — +0.5 puts the marker in the middle of its cell.
        return {
            px: (x + 0.5) * WorldMap.TILE_PX,
            py: (y + 0.5) * WorldMap.TILE_PX,
        }
    }
}
