import { Application, Container, FederatedPointerEvent, Graphics, Text, TextStyle } from 'pixi.js'
import type { CelestialBodyDto } from '../types/api'
import {
    CANVAS_PX as HELPER_CANVAS_PX,
    GRID_CELLS as HELPER_GRID_CELLS,
    TILE_PX as HELPER_TILE_PX,
    clamp,
    pixelsToCameraDelta,
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
    | { kind: 'body'; id: string }
    | null

/**
 * What's currently under the cursor when a hover tooltip should show.
 * Screen coordinates are canvas-relative — the React overlay positions the
 * tooltip element off these. Emitted with {@code null} when the cursor leaves
 * any entity, so the overlay can hide.
 */
export type HoverInfo =
    | { kind: 'ship'; ship: ShipOnMap; screenX: number; screenY: number }
    | { kind: 'body'; body: CelestialBodyDto; screenX: number; screenY: number }
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
 *   <li><b>Normal</b>: ship-and-body markers capture clicks ({@code
 *       onShipClick} / {@code onBodyClick} fire, the tile underneath does
 *       <i>not</i> fire). Empty-tile clicks call {@code onTileClick}. UI uses
 *       this for "select that thing".</li>
 *   <li><b>Targeting</b> (set via {@link setTargetingMode}): ships and bodies
 *       become transparent to pointer events ({@code eventMode = 'none'}), so
 *       a click anywhere — including on top of a body's disc or another
 *       ship's triangle — falls through to the background and fires
 *       {@code onTileClick} for the tile under the cursor. Lets the player
 *       MOVE-target a body's tile without the body "swallowing" the click.</li>
 * </ul>
 */
/**
 * Pan-key bindings for the keyboard navigation in {@link WorldMap}. Keyed by
 * {@code KeyboardEvent.code} so the bindings stay layout-independent — WASD
 * means the W/A/S/D positions on a QWERTY keyboard, the same physical keys
 * a Dvorak or Colemak user expects. Arrows are physical too, just by coincidence.
 */
const PAN_KEYS: Record<string, { dx: number; dy: number }> = {
    ArrowLeft:  { dx: -1, dy:  0 },
    ArrowRight: { dx:  1, dy:  0 },
    ArrowUp:    { dx:  0, dy: -1 },
    ArrowDown:  { dx:  0, dy:  1 },
    KeyA:       { dx: -1, dy:  0 },
    KeyD:       { dx:  1, dy:  0 },
    KeyW:       { dx:  0, dy: -1 },
    KeyS:       { dx:  0, dy:  1 },
}

/**
 * Skip the pan when the player is typing — pressing 'd' in the ship-name
 * editor shouldn't send the camera east. Covers native form inputs and any
 * contenteditable surface (none in v1 but future dialogs / chat would be).
 */
function isEditableTarget(el: Element | null): boolean {
    if (!el) return false
    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') return true
    return (el as HTMLElement).isContentEditable === true
}

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

    /**
     * Movement (in CSS pixels) past which an RMB press is treated as a drag
     * rather than a tap. Below the threshold the press fires the existing
     * {@code onRightClick} deselect; above it, the camera starts panning.
     */
    private static readonly DRAG_THRESHOLD_PX = 5

    /**
     * Camera-pan rate (in world tiles per second) while an arrow / WASD key
     * is held. Constant in world coordinates — visually slower at high zoom,
     * which matches the "more precision when inzoomed" intuition.
     */
    private static readonly KEYBOARD_PAN_TILES_PER_SECOND = 12

    private static readonly COLORS = {
        background: 0x0a0a1a,
        gridLine: 0x2a2a4a,
        bodyLabel: 0xffffff,
        // Per-kind body colors. The fallback (`body`) is also the legacy color
        // used before PR 3 broke kinds apart.
        body: 0xffaa00,
        bodyRocky: 0xb07050,    // brown
        bodyLava: 0xff5533,     // bright red-orange
        bodyIce: 0x99ccee,      // pale blue
        bodyGasGiant: 0xddaa66, // banded ochre
        bodyAsteroid: 0x888888, // muted grey
        bodyStar: 0xfff066,     // bright yellow (matches selected-ship for visual weight)
        shipOwn: 0x66ddff,
        shipForeign: 0xc266aa,
        shipSelected: 0xfff066,
        shipOutline: 0xffffff,
        hover: 0x66ddff,
        selectionRingShip: 0x66ddff,
        selectionRingBody: 0xffdd88,
    }

    /** Color picker keyed on the body's kind. Used by {@link renderBodies}. */
    private static colorForKind(kind: CelestialBodyDto['kind']): number {
        switch (kind) {
            case 'ROCKY_PLANET':
                return WorldMap.COLORS.bodyRocky
            case 'LAVA_PLANET':
                return WorldMap.COLORS.bodyLava
            case 'ICE_PLANET':
                return WorldMap.COLORS.bodyIce
            case 'GAS_GIANT':
                return WorldMap.COLORS.bodyGasGiant
            case 'ASTEROID':
                return WorldMap.COLORS.bodyAsteroid
            case 'STAR':
                return WorldMap.COLORS.bodyStar
            default:
                return WorldMap.COLORS.body
        }
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
     * Has the player panned the camera since the last explicit selection
     * change? Set by RMB drag and keyboard pan; cleared when {@link setSelection}
     * receives a new entity. While true, {@link setShips} / {@link setBodies}
     * skip the per-tick "snap camera back to the selected entity" so a
     * deliberately-panned view doesn't jerk back every poll.
     */
    private userHasPanned = false

    /**
     * The Pixi-mounted canvas. Held separately so listeners attached directly
     * to the DOM (wheel, contextmenu, pointer*) can be cleaned up on
     * {@link destroy}.
     */
    private canvas: HTMLCanvasElement | null = null
    private wheelHandler: ((e: WheelEvent) => void) | null = null
    private contextMenuHandler: ((e: Event) => void) | null = null
    private pointerDownHandler: ((e: PointerEvent) => void) | null = null
    private pointerMoveHandler: ((e: PointerEvent) => void) | null = null
    private pointerUpHandler: ((e: PointerEvent) => void) | null = null
    private keyDownHandler: ((e: KeyboardEvent) => void) | null = null
    private keyUpHandler: ((e: KeyboardEvent) => void) | null = null
    private blurHandler: (() => void) | null = null
    private tickerCallback: (() => void) | null = null

    /**
     * Pan keys currently held. Drives the camera-pan tick callback. Cleared
     * on window blur so alt-tabbing mid-press doesn't leave the camera
     * panning forever.
     */
    private heldPanKeys = new Set<string>()

    /**
     * Active RMB drag state. Non-null between a right-button {@code pointerdown}
     * and the matching {@code pointerup}. The {@code moved} flag flips true
     * once the cursor has traveled past {@link DRAG_THRESHOLD_PX} — that
     * irreversibly converts the gesture from a tap (which would deselect on
     * release) into a drag (which translates the camera and consumes the
     * release without firing {@code onRightClick}).
     */
    private rmbDrag: {
        startClientX: number
        startClientY: number
        lastClientX: number
        lastClientY: number
        moved: boolean
        pointerId: number
    } | null = null

    // Layers, all parented to worldLayer which carries the camera transform.
    private worldLayer: Container | null = null
    private gridLayer: Graphics | null = null
    private hoverLayer: Graphics | null = null
    private selectionLayer: Graphics | null = null
    private bodiesLayer: Container | null = null
    private shipsLayer: Container | null = null

    // Cached so we can re-render when selection / zoom changes without the
    // caller having to keep passing the same arrays.
    private bodies: CelestialBodyDto[] = []
    private ships: ShipOnMap[] = []
    private selection: MapSelection = null
    private hoverTile: { x: number; y: number } | null = null
    private targeting = false

    private onTileClick: ((x: number, y: number) => void) | null = null
    private onShipClick: ((ship: ShipOnMap) => void) | null = null
    private onBodyClick: ((body: CelestialBodyDto) => void) | null = null
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
        // exists on the DOM. RMB drag-pan also lives here so we can keep
        // receiving pointermove events when the cursor leaves the canvas
        // mid-drag (via setPointerCapture). All cleaned up in {@link destroy}.
        this.wheelHandler = (e: WheelEvent) => this.handleWheel(e)
        this.contextMenuHandler = (e: Event) => e.preventDefault()
        this.pointerDownHandler = (e: PointerEvent) => this.handlePointerDown(e)
        this.pointerMoveHandler = (e: PointerEvent) => this.handlePointerMove(e)
        this.pointerUpHandler = (e: PointerEvent) => this.handlePointerUp(e)
        this.canvas.addEventListener('wheel', this.wheelHandler, { passive: false })
        this.canvas.addEventListener('contextmenu', this.contextMenuHandler)
        this.canvas.addEventListener('pointerdown', this.pointerDownHandler)
        this.canvas.addEventListener('pointermove', this.pointerMoveHandler)
        this.canvas.addEventListener('pointerup', this.pointerUpHandler)
        this.canvas.addEventListener('pointercancel', this.pointerUpHandler)

        // Keyboard pan (#93). Listeners go on window so the canvas doesn't
        // need keyboard focus to receive them — the game grid is always the
        // primary input surface on this page. The Pixi ticker (already
        // running at rAF cadence) drives the per-frame camera translation
        // so we don't fight Pixi's animation loop with a parallel rAF.
        this.keyDownHandler = (e: KeyboardEvent) => this.handleKeyDown(e)
        this.keyUpHandler = (e: KeyboardEvent) => this.handleKeyUp(e)
        this.blurHandler = () => this.heldPanKeys.clear()
        this.tickerCallback = () => this.applyKeyboardPan()
        window.addEventListener('keydown', this.keyDownHandler)
        window.addEventListener('keyup', this.keyUpHandler)
        window.addEventListener('blur', this.blurHandler)
        app.ticker.add(this.tickerCallback)

        // World layer holds everything that moves/zooms with the camera.
        this.worldLayer = new Container()
        app.stage.addChild(this.worldLayer)

        // Grid first (bottom), hover next so it sits over the grid but under
        // bodies and ships — keeps the highlight from covering a body's
        // label or a ship's marker when the cursor lands on it. Selection ring
        // sits between hover and the entity layers so it's clearly visible
        // behind the marker but above the hover tile fill.
        this.gridLayer = new Graphics()
        this.hoverLayer = new Graphics()
        this.selectionLayer = new Graphics()
        this.bodiesLayer = new Container()
        this.shipsLayer = new Container()
        this.worldLayer.addChild(this.gridLayer)
        this.worldLayer.addChild(this.hoverLayer)
        this.worldLayer.addChild(this.selectionLayer)
        this.worldLayer.addChild(this.bodiesLayer)
        this.worldLayer.addChild(this.shipsLayer)

        this.applyCamera()
        this.drawGrid()
    }

    destroy(): void {
        if (this.canvas) {
            if (this.wheelHandler) {
                this.canvas.removeEventListener('wheel', this.wheelHandler)
            }
            if (this.contextMenuHandler) {
                this.canvas.removeEventListener('contextmenu', this.contextMenuHandler)
            }
            if (this.pointerDownHandler) {
                this.canvas.removeEventListener('pointerdown', this.pointerDownHandler)
            }
            if (this.pointerMoveHandler) {
                this.canvas.removeEventListener('pointermove', this.pointerMoveHandler)
            }
            if (this.pointerUpHandler) {
                this.canvas.removeEventListener('pointerup', this.pointerUpHandler)
                this.canvas.removeEventListener('pointercancel', this.pointerUpHandler)
            }
        }
        if (this.keyDownHandler) window.removeEventListener('keydown', this.keyDownHandler)
        if (this.keyUpHandler) window.removeEventListener('keyup', this.keyUpHandler)
        if (this.blurHandler) window.removeEventListener('blur', this.blurHandler)
        // Detach the ticker callback *before* app.destroy() below tears the
        // ticker down — otherwise we'd try to remove a listener from a
        // disposed ticker, no-op but noisy.
        if (this.app && this.tickerCallback) this.app.ticker.remove(this.tickerCallback)
        this.canvas = null
        this.wheelHandler = null
        this.contextMenuHandler = null
        this.pointerDownHandler = null
        this.pointerMoveHandler = null
        this.pointerUpHandler = null
        this.keyDownHandler = null
        this.keyUpHandler = null
        this.blurHandler = null
        this.tickerCallback = null
        this.heldPanKeys.clear()
        this.rmbDrag = null
        if (this.app) {
            this.app.destroy(true, { children: true, texture: true })
            this.app = null
            this.worldLayer = null
            this.gridLayer = null
            this.hoverLayer = null
            this.selectionLayer = null
            this.bodiesLayer = null
            this.shipsLayer = null
        }
    }

    setBodies(bodies: CelestialBodyDto[]): void {
        this.bodies = bodies
        this.renderBodies()
        // A body's position may be what the camera is locked to — recompute
        // in case the body entries arrived after the selection was set.
        // Skip if the player has panned manually since the last selection:
        // they're navigating away from the followed entity on purpose.
        if (this.selection?.kind === 'body' && !this.userHasPanned) {
            this.updateCameraForSelection()
            this.renderSelectionRing()
        }
    }

    setShips(ships: ShipOnMap[]): void {
        this.ships = ships
        // Ship coordinates change each tick; if the camera follows a ship, it
        // needs to track the latest position. Skip if the player has panned
        // manually since the last selection — see setBodies for the rationale.
        if (this.selection?.kind === 'ship' && !this.userHasPanned) {
            this.updateCameraForSelection()
            this.renderSelectionRing()
        }
        this.renderShips()
    }

    setSelection(selection: MapSelection): void {
        // A new explicit selection re-arms the camera-follow behaviour — the
        // player clicked a thing, so jump to it even if they were panning
        // around manually a moment ago.
        this.userHasPanned = false
        this.selection = selection
        this.updateCameraForSelection()
        this.renderShips()
        this.renderBodies()
        this.renderSelectionRing()
    }

    /**
     * Toggle pointer-event transparency on ships + bodies. In targeting mode,
     * clicks pass through markers to the background's tile-click handler — the
     * player can MOVE-target a body's tile without the body swallowing the
     * click. Normal mode restores marker-level handlers.
     */
    setTargetingMode(active: boolean): void {
        if (this.targeting === active) return
        this.targeting = active
        this.renderShips()
        this.renderBodies()
    }

    setOnTileClick(callback: ((x: number, y: number) => void) | null): void {
        this.onTileClick = callback
    }

    setOnShipClick(callback: ((ship: ShipOnMap) => void) | null): void {
        this.onShipClick = callback
    }

    setOnBodyClick(callback: ((body: CelestialBodyDto) => void) | null): void {
        this.onBodyClick = callback
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
     * Fired when the cursor enters or moves within a ship/body marker, and
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
        const body = this.bodies.find((p) => p.id === this.selection!.id)
        return body ? { x: body.x, y: body.y } : null
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

    private renderBodies(): void {
        if (!this.bodiesLayer) return
        this.bodiesLayer.removeChildren()

        for (const body of this.bodies) {
            const { px, py } = WorldMap.tileToPx(body.x, body.y)
            const color = WorldMap.colorForKind(body.kind)
            // Asteroids render smaller than full bodies — they're physically
            // smaller and the visual cue helps the player tell what they're
            // looking at at world zoom. Everything else uses the full marker.
            const visibleHalf =
                body.kind === 'ASTEROID'
                    ? WorldMap.MARKER_MAX_HALF * 0.55
                    : WorldMap.MARKER_MAX_HALF

            const dot = new Graphics()
            // Visible disc is bounded so it can't draw past its tile —
            // asteroids use a smaller cap (~55% of MARKER_MAX_HALF) to read as
            // physically smaller objects, other kinds use the full cap. The
            // transparent hit area below stays large for click accessibility
            // at world zoom regardless of the visible bound.
            dot.circle(px, py, 10)
            dot.fill({ color, alpha: 0 })
            dot.circle(px, py, visibleHalf)
            dot.fill(color)
            if (this.targeting) {
                // In targeting mode the body must NOT swallow the click —
                // a MOVE-targeting click on a body's tile is queued like any
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
                    // LMB only — RMB is owned by the DOM-level pointer
                    // handlers so the 5 px tap-vs-drag rule (#84) applies
                    // uniformly whether the press started on a marker or
                    // on empty grid. Stop propagation so an LMB hit on a
                    // body doesn't also fire a tile-click underneath.
                    if (e.button !== 0) return
                    e.stopPropagation()
                    this.onBodyClick?.(body)
                })
                dot.on('pointerover', (e: FederatedPointerEvent) =>
                    this.onEntityHover?.({
                        kind: 'body',
                        body,
                        screenX: e.global.x,
                        screenY: e.global.y,
                    })
                )
                dot.on('pointermove', (e: FederatedPointerEvent) =>
                    this.onEntityHover?.({
                        kind: 'body',
                        body,
                        screenX: e.global.x,
                        screenY: e.global.y,
                    })
                )
                dot.on('pointerout', () => this.onEntityHover?.(null))
            }
            this.bodiesLayer.addChild(dot)

            const label = new Text({
                text: body.name,
                style: new TextStyle({
                    fontFamily: 'system-ui, sans-serif',
                    fontSize: 9,
                    fill: WorldMap.COLORS.bodyLabel,
                }),
            })
            label.x = px + 6
            label.y = py - 5
            this.bodiesLayer.addChild(label)
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
                    // LMB only — see the matching comment on the body
                    // marker above. DOM-level pointer handlers own RMB.
                    if (e.button !== 0) return
                    e.stopPropagation()
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
        // RMB is handled by the DOM-level pointer handlers below — they own
        // the full tap-vs-drag distinction (#84). LMB stays here.
        if (event.button !== 0) return
        if (!this.onTileClick) return
        const tile = this.screenToTile(event.global.x, event.global.y)
        if (tile) this.onTileClick(tile.x, tile.y)
    }

    // ---------- RMB drag-pan (#84) ----------

    /**
     * RMB press starts a potential drag. We don't commit to "this is a pan"
     * yet — the tap-vs-drag decision happens on the first move past
     * {@link DRAG_THRESHOLD_PX}. Capturing the pointer means subsequent moves
     * outside the canvas (dragging past the edge) still route here.
     */
    private handlePointerDown(event: PointerEvent): void {
        if (event.button !== 2) return
        if (!this.canvas) return
        this.rmbDrag = {
            startClientX: event.clientX,
            startClientY: event.clientY,
            lastClientX: event.clientX,
            lastClientY: event.clientY,
            moved: false,
            pointerId: event.pointerId,
        }
        this.canvas.setPointerCapture(event.pointerId)
    }

    /**
     * RMB moved. While {@code rmbDrag} is active, translate the camera so
     * the world point under the cursor at press-time stays under the cursor.
     * Once {@code moved} flips true, the gesture is committed to "pan" —
     * the matching pointerup will <i>not</i> fire {@code onRightClick}.
     */
    private handlePointerMove(event: PointerEvent): void {
        if (!this.rmbDrag) return
        const dxFromStart = event.clientX - this.rmbDrag.startClientX
        const dyFromStart = event.clientY - this.rmbDrag.startClientY
        if (!this.rmbDrag.moved) {
            if (Math.max(Math.abs(dxFromStart), Math.abs(dyFromStart)) < WorldMap.DRAG_THRESHOLD_PX) {
                return
            }
            this.rmbDrag.moved = true
            this.setPanCursor(true)
        }
        // Per-step delta (not "from drag start") so the RMB pan composes with
        // concurrent keyboard pan — both contributions land on the current
        // camera state instead of the RMB handler clobbering keyboard pan on
        // every pointermove.
        const stepDx = event.clientX - this.rmbDrag.lastClientX
        const stepDy = event.clientY - this.rmbDrag.lastClientY
        this.rmbDrag.lastClientX = event.clientX
        this.rmbDrag.lastClientY = event.clientY
        const delta = pixelsToCameraDelta(stepDx, stepDy, this.camera.zoom, WorldMap.TILE_PX)
        this.camera = {
            x: clamp(this.camera.x + delta.dx, 0, WorldMap.GRID_CELLS),
            y: clamp(this.camera.y + delta.dy, 0, WorldMap.GRID_CELLS),
            zoom: this.camera.zoom,
        }
        // Mark as manually panned so the next setShips/setBodies tick
        // doesn't snap the camera back to a selected entity.
        this.userHasPanned = true
        this.applyCamera()
        this.drawGrid()
        this.renderSelectionRing()
    }

    /**
     * Release ends the RMB gesture. A tap (no movement past the threshold)
     * fires the existing {@code onRightClick} deselect — preserves the
     * historical RMB affordance. A drag just cleans up.
     *
     * <p>A {@code pointercancel} (e.g. OS focus-steal, browser-level abort)
     * resolves the same way as a drag: clean up state but <i>don't</i>
     * fire {@code onRightClick}. An aborted gesture isn't a user-intended
     * tap — silently deselecting on focus-steal would be confusing.
     */
    private handlePointerUp(event: PointerEvent): void {
        if (!this.rmbDrag || event.pointerId !== this.rmbDrag.pointerId) return
        const wasDrag = this.rmbDrag.moved
        if (this.canvas?.hasPointerCapture(event.pointerId)) {
            this.canvas.releasePointerCapture(event.pointerId)
        }
        this.rmbDrag = null
        this.setPanCursor(false)
        if (!wasDrag && event.type === 'pointerup') {
            this.onRightClick?.()
        }
    }

    /**
     * Toggle the grabbing cursor for pan mode. Skips the override while
     * targeting is active so the crosshair cursor (set via a CSS class on
     * the outer wrapper in Game.tsx) keeps precedence.
     */
    private setPanCursor(panning: boolean): void {
        if (!this.canvas) return
        if (panning && !this.targeting) {
            this.canvas.style.cursor = 'grabbing'
        } else {
            // Empty string removes the inline rule so the stylesheet wins
            // (crosshair in targeting mode, default otherwise).
            this.canvas.style.cursor = ''
        }
    }

    // ---------- keyboard pan (#93) ----------

    /**
     * Track the held pan key and {@code preventDefault} so arrow keys don't
     * scroll the page. No-op when the user is typing in a form input —
     * pressing 'd' in the ship-name editor should produce a 'd', not pan
     * the camera east.
     *
     * <p>Key repeat is harmless: {@code Set.add} is idempotent.
     */
    private handleKeyDown(event: KeyboardEvent): void {
        if (!(event.code in PAN_KEYS)) return
        // Skip modifier chords (Ctrl+D bookmark, Alt+Tab, etc.) so we don't
        // hijack browser/app shortcuts.
        if (event.ctrlKey || event.metaKey || event.altKey) return
        if (isEditableTarget(document.activeElement)) return
        // Only suppress the default for arrow keys — those scroll the page.
        // WASD already produces no default behaviour on the canvas, so leaving
        // it alone keeps text selection / browser shortcuts unaffected.
        if (event.code.startsWith('Arrow')) event.preventDefault()
        this.heldPanKeys.add(event.code)
    }

    private handleKeyUp(event: KeyboardEvent): void {
        if (!(event.code in PAN_KEYS)) return
        this.heldPanKeys.delete(event.code)
    }

    /**
     * Per-frame Pixi-ticker callback. Sums the direction vectors of all held
     * pan keys, normalises so diagonals don't move faster than axis-aligned
     * pans, scales by the frame delta and {@link KEYBOARD_PAN_TILES_PER_SECOND},
     * applies, clamps, redraws.
     *
     * <p>Bails fast in the common no-keys-held case so the ticker tax is
     * one map lookup + a size check.
     */
    private applyKeyboardPan(): void {
        if (this.heldPanKeys.size === 0 || !this.app) return
        // Per-tick focus guard: if the player held an arrow on the canvas and
        // then tabbed / clicked into an input mid-pan, keyup hasn't fired yet
        // so the key is still in the held set. Bail until focus leaves the
        // editable surface again.
        if (isEditableTarget(document.activeElement)) return
        let dxSum = 0
        let dySum = 0
        for (const code of this.heldPanKeys) {
            const v = PAN_KEYS[code]
            dxSum += v.dx
            dySum += v.dy
        }
        // Opposing keys (left + right, or W + S) cancel — no motion, but
        // also no userHasPanned latch. Treat this turn as "nothing happened".
        if (dxSum === 0 && dySum === 0) return
        const deltaSec = this.app.ticker.deltaMS / 1000
        const distance = WorldMap.KEYBOARD_PAN_TILES_PER_SECOND * deltaSec
        // Normalise so diagonal speed matches axis-aligned speed end-to-end,
        // not per-axis. Without this, "up + right" panned ~1.41× as fast.
        const norm = Math.hypot(dxSum, dySum)
        this.camera = {
            x: clamp(this.camera.x + (dxSum / norm) * distance, 0, WorldMap.GRID_CELLS),
            y: clamp(this.camera.y + (dySum / norm) * distance, 0, WorldMap.GRID_CELLS),
            zoom: this.camera.zoom,
        }
        // Mark manual pan so setShips/setBodies stop snapping the camera
        // back to a selected entity. Cleared on the next explicit setSelection.
        this.userHasPanned = true
        this.applyCamera()
        this.drawGrid()
        this.renderSelectionRing()
    }

    /**
     * Mouse-wheel zoom. Clamped to {@link ZOOM_MIN}…{@link ZOOM_MAX}. When the
     * player has no selection, the zoom is centred on the cursor (the world
     * tile under the cursor stays fixed). When something is selected the
     * camera is selection-locked, so we just change zoom and let the next
     * {@link setShips}/{@link setBodies} re-snap as the entity moves.
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
        // it here would just be undone on the next setShips/setBodies call.
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
     * Ship selections get a cyan ring ({@code selectionRingShip}); body
     * selections get an amber ring ({@code selectionRingBody}). The ring
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
                color: WorldMap.COLORS.selectionRingBody,
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
