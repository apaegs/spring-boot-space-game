import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { WorldMap, type HoverInfo, type MapSelection, type ShipOnMap } from '../pixi/WorldMap'
import type { PlanetDto } from '../types/api'

type WorldMapViewProps = {
    planets: PlanetDto[]
    /**
     * Every ship to render — caller's own + every other player's, already
     * deduped and tagged with {@code isOwn} by the parent. WorldMap doesn't
     * fetch; it just draws what it's handed.
     */
    ships: ShipOnMap[]
    /** What the player has selected. Drives camera focus and ship-marker highlight. */
    selection: MapSelection
    /**
     * Whether the map is in targeting mode (Move/Land action awaiting a tile
     * pick). Affects pointer-event capture inside the map: in targeting mode,
     * ship and planet markers don't swallow clicks — the tile underneath them
     * fires {@code onTileClick} instead.
     */
    isTargeting: boolean
    /** Click on any tile (background or otherwise). Used for targeting-mode. */
    onTileClick?: (x: number, y: number) => void
    /** Click on a ship marker. Used by the parent to select that ship. */
    onShipClick?: (ship: ShipOnMap) => void
    /** Click on a planet marker. Used by the parent to select that planet. */
    onPlanetClick?: (planet: PlanetDto) => void
    /**
     * Right-mouse-button anywhere on the map. RTS-style "cancel / deselect"
     * — the parent decides what to do (cancel targeting in targeting mode,
     * otherwise clear the current selection).
     */
    onRightClick?: () => void
}

/**
 * Delay before a tooltip appears, in ms. Long enough that brushing past a
 * marker during panning/targeting doesn't flash a tooltip, short enough that
 * a deliberate hover feels responsive.
 */
const TOOLTIP_DELAY_MS = 300

/**
 * React wrapper around the PixiJS {@link WorldMap}. Owns the mount/unmount
 * lifecycle and forwards prop changes through the imperative API.
 *
 * <p>Props are mirrored into refs so the async {@code map.init()} lifecycle
 * can apply the latest values when it resolves. Without that, the very first
 * payload of planets/ships dropped on the floor: the {@code setPlanets}
 * effect fires while {@code mapRef.current} is still null (init hadn't
 * resolved yet), so the call is a no-op, and there's no re-render to retry it.
 *
 * <p>This component also renders the hover-tooltip overlay. WorldMap reports
 * what the cursor is on via {@code setOnEntityHover}, and we render a debounced
 * tooltip div positioned in canvas-relative coordinates.
 */
export function WorldMapView({
    planets,
    ships,
    selection,
    isTargeting,
    onTileClick,
    onShipClick,
    onPlanetClick,
    onRightClick,
}: WorldMapViewProps) {
    const containerRef = useRef<HTMLDivElement | null>(null)
    const mapRef = useRef<WorldMap | null>(null)

    // Mirror every prop into a ref so we can read the freshest value from
    // inside the async init().then handler — which may run after this render
    // and any subsequent renders.
    const planetsRef = useRef(planets)
    const shipsRef = useRef(ships)
    const selectionRef = useRef(selection)
    const isTargetingRef = useRef(isTargeting)
    const onTileClickRef = useRef(onTileClick)
    const onShipClickRef = useRef(onShipClick)
    const onPlanetClickRef = useRef(onPlanetClick)
    const onRightClickRef = useRef(onRightClick)
    useEffect(() => {
        planetsRef.current = planets
    }, [planets])
    useEffect(() => {
        shipsRef.current = ships
    }, [ships])
    useEffect(() => {
        selectionRef.current = selection
    }, [selection])
    useEffect(() => {
        isTargetingRef.current = isTargeting
    }, [isTargeting])
    useEffect(() => {
        onTileClickRef.current = onTileClick
    }, [onTileClick])
    useEffect(() => {
        onShipClickRef.current = onShipClick
    }, [onShipClick])
    useEffect(() => {
        onPlanetClickRef.current = onPlanetClick
    }, [onPlanetClick])
    useEffect(() => {
        onRightClickRef.current = onRightClick
    }, [onRightClick])

    // Tooltip state. {@code shownTooltip} is what's actually rendered after
    // the debounce. We keep the timeout in a ref so a fast move between
    // entities can reset the timer without React re-running an effect. State
    // setters are stable, so the inline closure in {@code init().then} below
    // captures them safely once.
    const [shownTooltip, setShownTooltip] = useState<HoverInfo>(null)
    const tooltipTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

    useEffect(() => {
        const container = containerRef.current
        if (!container) return

        let cancelled = false
        const map = new WorldMap()
        void map.init(container).then(() => {
            if (cancelled) {
                map.destroy()
                return
            }
            // Apply the latest props (via refs) now that the renderer is
            // alive. Otherwise the prop-watching effects below would have
            // already fired against a null mapRef and the initial payload
            // would be missing.
            map.setTargetingMode(isTargetingRef.current)
            map.setPlanets(planetsRef.current)
            map.setShips(shipsRef.current)
            map.setSelection(selectionRef.current)
            map.setOnTileClick((x, y) => onTileClickRef.current?.(x, y))
            map.setOnShipClick((ship) => onShipClickRef.current?.(ship))
            map.setOnPlanetClick((planet) => onPlanetClickRef.current?.(planet))
            map.setOnRightClick(() => onRightClickRef.current?.())
            // Always-debounce: every hover-change resets the timer; hover-leave
            // hides immediately so a quick brush past a marker doesn't leave
            // a tooltip dangling. Logic inlined here so this closure captures
            // only the stable setter and the timer ref — no per-render fn.
            map.setOnEntityHover((info) => {
                if (tooltipTimerRef.current) {
                    clearTimeout(tooltipTimerRef.current)
                    tooltipTimerRef.current = null
                }
                if (info === null) {
                    setShownTooltip(null)
                    return
                }
                tooltipTimerRef.current = setTimeout(() => {
                    setShownTooltip(info)
                    tooltipTimerRef.current = null
                }, TOOLTIP_DELAY_MS)
            })
            mapRef.current = map
        })

        return () => {
            cancelled = true
            mapRef.current?.destroy()
            mapRef.current = null
            if (tooltipTimerRef.current) {
                clearTimeout(tooltipTimerRef.current)
                tooltipTimerRef.current = null
            }
        }
    }, [])

    // Subsequent updates: once mapRef is set, push through normally. The
    // first render's payload was already applied inside init().then above.
    useEffect(() => {
        mapRef.current?.setPlanets(planets)
    }, [planets])

    useEffect(() => {
        mapRef.current?.setShips(ships)
    }, [ships])

    useEffect(() => {
        mapRef.current?.setSelection(selection)
    }, [selection])

    useEffect(() => {
        mapRef.current?.setTargetingMode(isTargeting)
        // Entering targeting mode kills any pending tooltip timer. The
        // displayed tooltip itself is gated on {@code !isTargeting} below,
        // so no setState here — keeps this effect purely about syncing
        // external state, not driving renders.
        if (isTargeting && tooltipTimerRef.current) {
            clearTimeout(tooltipTimerRef.current)
            tooltipTimerRef.current = null
        }
    }, [isTargeting])

    return (
        <div ref={containerRef} className="world-map">
            {shownTooltip && !isTargeting && <Tooltip info={shownTooltip} />}
        </div>
    )
}

/** Gap between cursor and tooltip box, mirrored on flip. */
const TOOLTIP_OFFSET_PX = 12

/**
 * Floating label anchored next to the cursor. Pure presentation — the WorldMap
 * drives content and position via {@link WorldMapView}'s state. The CSS class
 * lives in App.css alongside the rest of the game styling.
 *
 * <p>Position is clamped to the {@code .world-map} container: when the default
 * below-right anchor would overflow the container's right/bottom edge, the
 * tooltip flips to the opposite side of the cursor. Width and height are
 * measured at render time with {@code useLayoutEffect}, so the clamped style
 * lands before the browser paints — no visible flicker.
 */
function Tooltip({ info }: { info: NonNullable<HoverInfo> }) {
    const ref = useRef<HTMLDivElement | null>(null)
    // Initial render is hidden: we don't know the tooltip's measured size yet,
    // so any unclamped position could flash off-screen. The layout effect
    // below measures and reveals before paint.
    const [style, setStyle] = useState<CSSProperties>({
        left: info.screenX + TOOLTIP_OFFSET_PX,
        top: info.screenY + TOOLTIP_OFFSET_PX,
        visibility: 'hidden',
    })

    useLayoutEffect(() => {
        const el = ref.current
        const parent = el?.parentElement
        if (!el || !parent) return
        const rect = el.getBoundingClientRect()
        const wouldOverflowRight =
            info.screenX + TOOLTIP_OFFSET_PX + rect.width > parent.clientWidth
        const wouldOverflowBottom =
            info.screenY + TOOLTIP_OFFSET_PX + rect.height > parent.clientHeight
        setStyle({
            left: wouldOverflowRight
                ? info.screenX - rect.width - TOOLTIP_OFFSET_PX
                : info.screenX + TOOLTIP_OFFSET_PX,
            top: wouldOverflowBottom
                ? info.screenY - rect.height - TOOLTIP_OFFSET_PX
                : info.screenY + TOOLTIP_OFFSET_PX,
            visibility: 'visible',
        })
    }, [info])

    const label =
        info.kind === 'ship' ? (
            <>
                <span className="world-map__tooltip-name">{info.ship.name}</span>
                {info.ship.isOwn && <span className="world-map__tooltip-tag">yours</span>}
            </>
        ) : (
            <span className="world-map__tooltip-name">{info.planet.name}</span>
        )

    return (
        <div ref={ref} className="world-map__tooltip" style={style} role="tooltip">
            {label}
        </div>
    )
}
