import { Application, Container, FederatedPointerEvent, Graphics, Text, TextStyle } from 'pixi.js'
import type { PlanetDto } from '../types/api'
import {
    CANVAS_PX as HELPER_CANVAS_PX,
    GRID_CELLS as HELPER_GRID_CELLS,
    TILE_PX as HELPER_TILE_PX,
    clamp,
    tileToPx as pureTileToPx,
} from './WorldMap.helpers'

/**
 * Map-level view model for any ship — own or foreign. The React layer projects
 * its {@code ShipDto} / {@code PublicShipDto} sources into this uniform shape
 * before handing them down. {@code isOwn} drives the marker color and (in the
 * future) any owner-only affordances; nothing about the click handling cares
 * about ownership.
 */
export type ShipOnMap = {
    id: string
    name: string
    x: number
    y: number
    isOwn: boolean
}

/**
 * Map-level selection. Mirrors {@code Selection} in the React layer but kept
 * separate so WorldMap stays React-free (no shared types with components).
 * Drives camera focus and marker highlight.
 */
export type MapSelection =
    | { kind: 'ship'; id: string }
    | { kind: 'planet'; id: string }
    | null

/**
 * What's currently under the cursor when a hover tooltip should show.
 * Screen coordinates are canvas-relative — the React overlay positions the
 * tooltip element off these. Emitted with {@code null} when the cursor leaves
 * any entity, so the overlay can hide.
 */
export type HoverInfo =
    | { kind: 'ship'; ship: ShipOnMap; screenX: number; screenY: number }
    | { kind: 'planet'; planet: PlanetDto; screenX: number; screenY: number }
    | null

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
 * <h3>Click semantics</h3>
 * Two modes:
 * <ul>
 *   <li><b>Normal</b>: ship-and-planet markers capture clicks ({@code
 *       onShipClick} / {@code onPlanetClick} fire, the tile underneath does
 *       <i>not</i> fire). Empty-tile clicks call {@code onTileClick}. UI uses
 *       this for "select that thing".</li>
 *   <li><b>Targeting</b> (set via {@link setTargetingMode}): ships and planets
 *       become transparent to pointer events ({@code eventMode = 'none'}), so
 *       a click anywhere — including on top of a planet's disc or another
 *       ship's triangle — falls through to the background and fires
 *       {@code onTileClick} for the tile under the cursor. Lets the player
 *       MOVE-target a planet's tile without the planet "swallowing" the click.</li>
 * </ul>
 */
export class WorldMap {
    // Sourced from WorldMap.helpers so the `CANVAS_PX === GRID_CELLS * TILE_PX`
    // invariant test in WorldMap.helpers.test.ts actually protects this class's
    // geometry — defining them locally would let the two copies drift silently.
    private static readonly CANVAS_PX = HELPER_CANVAS_PX
    private static readonly GRID_CELLS = HELPER_GRID_CELLS
    private static readonly TILE_PX = HELPER_TILE_PX // base size at zoom 1.0; world = 600px

    /**
     * Hard cap for any visible marker's half-extent so nothing renders larger
     * than a grid tile. {@code TILE_PX/2 - 0.5} leaves a half-pixel margin on
     * each side, keeping markers visually inside their cell at all zoom levels.
     */
    private static readonly MARKER_MAX_HALF = WorldMap.TILE_PX / 2 - 0.5

    /**
     * Zoom bounds. ZOOM_MIN = 1.0 fits the full 100×100 world in the 600px
     * canvas. ZOOM_MAX = 10.0 gives a ~10×10-tile close-up (canvas / tile / 10).
     * SELECTED_DEFAULT is the zoom level a fresh selection snaps to <i>only</i>
     * when the player hasn't actively zoomed yet — once they touch the wheel,
     * their level is preserved across selection changes.
     */
    private static readonly ZOOM_MIN = 1.0
    private static readonly ZOOM_MAX = 10.0
    private static readonly ZOOM_SELECTED_DEFAULT = 4.0
    /**
     * Multiplicative step per wheel notch. ~10 notches go MIN→MAX, which is
     * close enough to "a few flicks of the wheel" without feeling jumpy.
     */
    private static readonly ZOOM_WHEEL_STEP = 1.25

    private static readonly COLORS = {
        background: 0x0a0a1a,
        gridLine: 0x2a2a4a,
        planet: 0xffaa00,
        planetLabel: 0xffffff,
        shipOwn: 0x66ddff,
        shipForeign: 0xc266aa,
        shipSelected: 0xfff066,
        shipOutline: 0xffffff,
        hover: 0x66ddff,
        selectionRingShip: 0x66ddff,
        selectionRingPlanet: 0xffdd88,
    }

    private app: Application | null = null

    // Camera state. The world layer is translated/scaled so that the tile at
    // (camera.x, camera.y) ends up at the canvas centre. Zoom is user-driven
    // (wheel) but bounded by ZOOM_MIN/ZOOM_MAX; position is selection-driven.
    private camera = { x: 50, y: 50, zoom: WorldMap.ZOOM_MIN }

    /**
     * Has the player adjusted zoom manually? Used to decide whether a fresh
     * selection should snap to {@link ZOOM_SELECTED_DEFAULT} (no — keep the
     * "click ship → zoom in" feel) or preserve the user's current zoom (yes —
     * don't yank the view they just set).
     */
    private userHasZoomed = false

    /**
     * The Pixi-mounted canvas. Held separately so listeners attached directly
     * to the DOM (wheel, contextmenu) can be cleaned up on {@link destroy}.
     */
    private canvas: HTMLCanvasElement | null = null
    private wheelHandler: ((e: WheelEvent) => void) | null = null
    private contextMenuHandler: ((e: Event) => void) | null = null

    // Layers, all parented to worldLayer which carries the camera transform.
    private worldLayer: Container | null = null
    private gridLayer: Graphics | null = null
    private hoverLayer: Graphics | null = null
    private selectionLayer: Graphics | null = null
    private planetsLayer: Container | null = null
    private shipsLayer: Container | null = null

    // Cached so we can re-render when selection / zoom changes without the
    // caller having to keep passing the same arrays.
    private planets: PlanetDto[] = []
    private ships: ShipOnMap[] = []
    private selection: MapSelection = null
    private hoverTile: { x: number; y: number } | null = null
    private targeting = false

    private onTileClick: ((x: number, y: number) => void) | null = null
    private onShipClick: ((ship: ShipOnMap) => void) | null = null
    private onPlanetClick: ((planet: PlanetDto) => void) | null = null
    private onRightClick: (() => void) | null = null
    private onEntityHover: ((info: HoverInfo) => void) | null = null

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
        this.canvas = app.canvas as HTMLCanvasElement

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

        // DOM-level listeners. Wheel and contextmenu don't surface through
        // Pixi's federated event system in a way we can use here — wheel
        // needs preventDefault to stop page scroll, and contextmenu only
        // exists on the DOM. Both are cleaned up in {@link destroy}.
        this.wheelHandler = (e: WheelEvent) => this.handleWheel(e)
        this.contextMenuHandler = (e: Event) => e.preventDefault()
        this.canvas.addEventListener('wheel', this.wheelHandler, { passive: false })
        this.canvas.addEventListener('contextmenu', this.contextMenuHandler)

        // World layer holds everything that moves/zooms with the camera.
        this.worldLayer = new Container()
        app.stage.addChild(this.worldLayer)

        // Grid first (bottom), hover next so it sits over the grid but under
        // planets and ships — keeps the highlight from covering a planet's
        // label or a ship's marker when the cursor lands on it. Selection ring
        // sits between hover and the entity layers so it's clearly visible
        // behind the marker but above the hover tile fill.
        this.gridLayer = new Graphics()
        this.hoverLayer = new Graphics()
        this.selectionLayer = new Graphics()
        this.planetsLayer = new Container()
        this.shipsLayer = new Container()
        this.worldLayer.addChild(this.gridLayer)
        this.worldLayer.addChild(this.hoverLayer)
        this.worldLayer.addChild(this.selectionLayer)
        this.worldLayer.addChild(this.planetsLayer)
        this.worldLayer.addChild(this.shipsLayer)

        this.applyCamera()
        this.drawGrid()
    }

    destroy(): void {
        if (this.canvas && this.wheelHandler) {
            this.canvas.removeEventListener('wheel', this.wheelHandler)
        }
        if (this.canvas && this.contextMenuHandler) {
            this.canvas.removeEventListener('contextmenu', this.contextMenuHandler)
        }
        this.canvas = null
        this.wheelHandler = null
        this.contextMenuHandler = null
        if (this.app) {
            this.app.destroy(true, { children: true, texture: true })
            this.app = null
            this.worldLayer = null
            this.gridLayer = null
            this.hoverLayer = null
            this.selectionLayer = null
            this.planetsLayer = null
            this.shipsLayer = null
        }
    }

    setPlanets(planets: PlanetDto[]): void {
        this.planets = planets
        this.renderPlanets()
        // A planet's position may be what the camera is locked to — recompute
        // in case the planet entries arrived after the selection was set.
        if (this.selection?.kind === 'planet') {
            this.updateCameraForSelection()
            this.renderSelectionRing()
        }
    }

    setShips(ships: ShipOnMap[]): void {
        this.ships = ships
        // Ship coordinates change each tick; if the camera follows a ship, it
        // needs to track the latest position.
        if (this.selection?.kind === 'ship') {
            this.updateCameraForSelection()
            this.renderSelectionRing()
        }
        this.renderShips()
    }

    setSelection(selection: MapSelection): void {
        this.selection = selection
        this.updateCameraForSelection()
        this.renderShips()
        this.renderPlanets()
        this.renderSelectionRing()
    }

    /**
     * Toggle pointer-event transparency on ships + planets. In targeting mode,
     * clicks pass through markers to the background's tile-click handler — the
     * player can MOVE-target a planet's tile without the planet swallowing the
     * click. Normal mode restores marker-level handlers.
     */
    setTargetingMode(active: boolean): void {
        if (this.targeting === active) return
        this.targeting = active
        this.renderShips()
        this.renderPlanets()
    }

    setOnTileClick(callback: ((x: number, y: number) => void) | null): void {
        this.onTileClick = callback
    }

    setOnShipClick(callback: ((ship: ShipOnMap) => void) | null): void {
        this.onShipClick = callback
    }

    setOnPlanetClick(callback: ((planet: PlanetDto) => void) | null): void {
        this.onPlanetClick = callback
    }

    /**
     * Fired on any right-mouse-button press anywhere on the map — over a
     * marker or empty tile, in any mode. The browser context menu is
     * suppressed on the canvas so this is the only effect of a right click.
     */
    setOnRightClick(callback: (() => void) | null): void {
        this.onRightClick = callback
    }

    /**
     * Fired when the cursor enters or moves within a ship/planet marker, and
     * with {@code null} when it leaves. Used by the React layer to drive
     * a tooltip overlay. Markers go {@code eventMode = 'none'} in targeting
     * mode, so this naturally stays silent there.
     */
    setOnEntityHover(callback: ((info: HoverInfo) => void) | null): void {
        this.onEntityHover = callback
    }

    // ---------- camera ----------

    private updateCameraForSelection(): void {
        const focus = this.resolveSelectionPosition()
        if (focus === null) {
            // Cleared selection: re-center on the world. Preserve the user's
            // zoom — yanking it back to ZOOM_MIN every time you click "off"
            // would be hostile if they'd zoomed in deliberately.
            this.camera = { x: 50, y: 50, zoom: this.camera.zoom }
        } else {
            // Snap the camera to the entity. Only auto-zoom if the player
            // hasn't touched the wheel yet — keeps the original "click ship →
            // zoom in" feel without overriding a user-chosen level later.
            const nextZoom = this.userHasZoomed
                ? this.camera.zoom
                : WorldMap.ZOOM_SELECTED_DEFAULT
            this.camera = { x: focus.x, y: focus.y, zoom: nextZoom }
        }
        this.applyCamera()
        // Grid density depends on zoom; redraw so per-tile lines appear/vanish.
        this.drawGrid()
    }

    /**
     * Resolve the current selection to a grid position, or null if nothing is
     * selected / the entity can't be found in the current data set. Lets the
     * camera fall back cleanly when a refetch removes a row mid-selection.
     */
    private resolveSelectionPosition(): { x: number; y: number } | null {
        if (!this.selection) return null
        if (this.selection.kind === 'ship') {
            const ship = this.ships.find((s) => s.id === this.selection!.id)
            return ship ? { x: ship.x, y: ship.y } : null
        }
        const planet = this.planets.find((p) => p.id === this.selection!.id)
        return planet ? { x: planet.x, y: planet.y } : null
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

        // One uniform thickness across the whole grid. The every-10 lines are
        // brighter (alpha 1.0) than the per-tile lines (alpha 0.35) so they
        // still read as a scale hint, but they don't read as different lines.
        // Per-tile lines only draw when zoomed in enough to actually see them;
        // at world zoom they collapse to a fog and just add visual noise.
        const lineWidth = 1
        if (this.camera.zoom >= 2.0) {
            for (let i = 0; i <= WorldMap.GRID_CELLS; i++) {
                if (i % 10 === 0) continue // major drawn separately below
                const offset = i * WorldMap.TILE_PX
                this.gridLayer.moveTo(offset, 0).lineTo(offset, total)
                this.gridLayer.moveTo(0, offset).lineTo(total, offset)
            }
            this.gridLayer.stroke({
                color: WorldMap.COLORS.gridLine,
                alpha: 0.35,
                width: lineWidth,
            })
        }

        for (let i = 0; i <= WorldMap.GRID_CELLS; i += 10) {
            const offset = i * WorldMap.TILE_PX
            this.gridLayer.moveTo(offset, 0).lineTo(offset, total)
            this.gridLayer.moveTo(0, offset).lineTo(total, offset)
        }
        this.gridLayer.stroke({ color: WorldMap.COLORS.gridLine, alpha: 1, width: lineWidth })
    }

    private renderPlanets(): void {
        if (!this.planetsLayer) return
        this.planetsLayer.removeChildren()

        for (const planet of this.planets) {
            const { px, py } = WorldMap.tileToPx(planet.x, planet.y)

            const dot = new Graphics()
            // Visible disc capped at {@link MARKER_MAX_HALF} so it can't draw
            // past its tile. The transparent hit area stays large for click
            // accessibility at world zoom — only the visible bound is capped.
            dot.circle(px, py, 10)
            dot.fill({ color: WorldMap.COLORS.planet, alpha: 0 })
            dot.circle(px, py, WorldMap.MARKER_MAX_HALF)
            dot.fill(WorldMap.COLORS.planet)
            if (this.targeting) {
                // In targeting mode the planet must NOT swallow the click —
                // a MOVE-targeting click on a planet's tile is queued like any
                // other tile click. Setting eventMode 'none' lets the click
                // fall through to the background's hit area. Hover/right-click
                // both intentionally drop out here too: no tooltips during
                // targeting (too noisy), and right-click cancels via the
                // background handler instead.
                dot.eventMode = 'none'
            } else {
                dot.eventMode = 'static'
                dot.cursor = 'pointer'
                dot.on('pointerdown', (e: FederatedPointerEvent) => {
                    e.stopPropagation() // don't fall through to the tile click
                    if (e.button === 2) {
                        this.onRightClick?.()
                        return
                    }
                    if (e.button !== 0) return
                    this.onPlanetClick?.(planet)
                })
                dot.on('pointerover', (e: FederatedPointerEvent) =>
                    this.onEntityHover?.({
                        kind: 'planet',
                        planet,
                        screenX: e.global.x,
                        screenY: e.global.y,
                    })
                )
                dot.on('pointermove', (e: FederatedPointerEvent) =>
                    this.onEntityHover?.({
                        kind: 'planet',
                        planet,
                        screenX: e.global.x,
                        screenY: e.global.y,
                    })
                )
                dot.on('pointerout', () => this.onEntityHover?.(null))
            }
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

        const selectedShipId = this.selection?.kind === 'ship' ? this.selection.id : null
        for (const ship of this.ships) {
            const isSelected = ship.id === selectedShipId
            const { px, py } = WorldMap.tileToPx(ship.x, ship.y)

            const marker = new Graphics()
            // Triangle pointing up. Size is capped at {@link MARKER_MAX_HALF}
            // so the marker can't draw past its tile (no more "ships span two
            // grid cells"). Selection is signalled by color (yellow) plus the
            // outline, not by inflating the marker — keeps the grid honest.
            const size = WorldMap.MARKER_MAX_HALF
            marker.poly([px, py - size, px + size, py + size, px - size, py + size])
            const fillColor = isSelected
                ? WorldMap.COLORS.shipSelected
                : ship.isOwn
                  ? WorldMap.COLORS.shipOwn
                  : WorldMap.COLORS.shipForeign
            marker.fill(fillColor)
            marker.stroke({ color: WorldMap.COLORS.shipOutline, width: 1 })
            if (this.targeting) {
                marker.eventMode = 'none'
            } else {
                marker.eventMode = 'static'
                marker.cursor = 'pointer'
                marker.on('pointerdown', (e: FederatedPointerEvent) => {
                    e.stopPropagation()
                    if (e.button === 2) {
                        this.onRightClick?.()
                        return
                    }
                    if (e.button !== 0) return
                    this.onShipClick?.(ship)
                })
                marker.on('pointerover', (e: FederatedPointerEvent) =>
                    this.onEntityHover?.({
                        kind: 'ship',
                        ship,
                        screenX: e.global.x,
                        screenY: e.global.y,
                    })
                )
                marker.on('pointermove', (e: FederatedPointerEvent) =>
                    this.onEntityHover?.({
                        kind: 'ship',
                        ship,
                        screenX: e.global.x,
                        screenY: e.global.y,
                    })
                )
                marker.on('pointerout', () => this.onEntityHover?.(null))
            }
            this.shipsLayer.addChild(marker)
        }
    }

    // ---------- input ----------

    private handleBackgroundClick(event: FederatedPointerEvent): void {
        // Right-click is universal "cancel / deselect" — it's the only effect
        // a right press has, regardless of what's underneath.
        if (event.button === 2) {
            this.onRightClick?.()
            return
        }
        if (event.button !== 0) return // middle / aux buttons: ignore
        if (!this.onTileClick) return
        const tile = this.screenToTile(event.global.x, event.global.y)
        if (tile) this.onTileClick(tile.x, tile.y)
    }

    /**
     * Mouse-wheel zoom. Clamped to {@link ZOOM_MIN}…{@link ZOOM_MAX}. When the
     * player has no selection, the zoom is centred on the cursor (the world
     * tile under the cursor stays fixed). When something is selected the
     * camera is selection-locked, so we just change zoom and let the next
     * {@link setShips}/{@link setPlanets} re-snap as the entity moves.
     */
    private handleWheel(event: WheelEvent): void {
        if (!this.canvas) return
        event.preventDefault()

        const direction = event.deltaY < 0 ? 1 : -1
        const factor = direction > 0 ? WorldMap.ZOOM_WHEEL_STEP : 1 / WorldMap.ZOOM_WHEEL_STEP
        const oldZoom = this.camera.zoom
        const newZoom = clamp(oldZoom * factor, WorldMap.ZOOM_MIN, WorldMap.ZOOM_MAX)
        if (newZoom === oldZoom) return

        // Cursor-centered: pin the world point under the cursor across the
        // zoom change. Only applies when there's no selection — with a
        // selection active the camera is locked to the entity and shifting
        // it here would just be undone on the next setShips/setPlanets call.
        if (this.selection === null) {
            const rect = this.canvas.getBoundingClientRect()
            const cursorX = event.clientX - rect.left
            const cursorY = event.clientY - rect.top
            const halfCanvas = WorldMap.CANVAS_PX / 2
            const worldPx = (cursorX - halfCanvas) / oldZoom + this.camera.x * WorldMap.TILE_PX
            const worldPy = (cursorY - halfCanvas) / oldZoom + this.camera.y * WorldMap.TILE_PX
            this.camera = {
                x: (worldPx - (cursorX - halfCanvas) / newZoom) / WorldMap.TILE_PX,
                y: (worldPy - (cursorY - halfCanvas) / newZoom) / WorldMap.TILE_PX,
                zoom: newZoom,
            }
        } else {
            this.camera = { ...this.camera, zoom: newZoom }
        }
        this.userHasZoomed = true
        this.applyCamera()
        this.drawGrid()
        this.renderSelectionRing()
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
     * Draw a static outline ring around the currently selected entity.
     * Ship selections get a cyan ring ({@code selectionRingShip}); planet
     * selections get an amber ring ({@code selectionRingPlanet}). The ring
     * radius is slightly larger than the marker so it's visible without
     * overlapping the entity geometry. Clears when nothing is selected.
     */
    private renderSelectionRing(): void {
        if (!this.selectionLayer) return
        this.selectionLayer.clear()
        if (!this.selection) return

        const pos = this.resolveSelectionPosition()
        if (!pos) return

        const { px, py } = WorldMap.tileToPx(pos.x, pos.y)
        const ringRadius = WorldMap.MARKER_MAX_HALF + 1.5

        if (this.selection.kind === 'ship') {
            this.selectionLayer.circle(px, py, ringRadius)
            this.selectionLayer.stroke({
                color: WorldMap.COLORS.selectionRingShip,
                alpha: 0.85,
                width: 1,
            })
        } else {
            this.selectionLayer.circle(px, py, ringRadius)
            this.selectionLayer.stroke({
                color: WorldMap.COLORS.selectionRingPlanet,
                alpha: 0.85,
                width: 1,
            })
        }
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
        return pureTileToPx(x, y, WorldMap.TILE_PX)
    }
}
