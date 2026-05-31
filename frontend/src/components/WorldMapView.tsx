import { useEffect, useRef } from 'react'
import { WorldMap } from '../pixi/WorldMap'
import type { PlanetDto, ShipDto } from '../types/api'

type WorldMapViewProps = {
    planets: PlanetDto[]
    ships: ShipDto[]
    selectedShipId: string | null
    /** Click on any tile (background or otherwise). Used for targeting-mode. */
    onTileClick?: (x: number, y: number) => void
    /** Click on a planet specifically — stops propagation so the tile click
     * doesn't also fire. */
    onPlanetClick?: (planet: PlanetDto) => void
}

/**
 * React wrapper around the PixiJS {@link WorldMap}. Owns the mount/unmount
 * lifecycle and forwards prop changes through the imperative API.
 *
 * <p>Props are mirrored into refs so the async {@code map.init()} lifecycle
 * can apply the latest values when it resolves. Without that, the very first
 * payload of planets/ships dropped on the floor: the {@code setPlanets}
 * effect fires while {@code mapRef.current} is still null (init hadn't
 * resolved yet), so the call is a no-op, and there's no re-render to retry it.
 */
export function WorldMapView({
    planets,
    ships,
    selectedShipId,
    onTileClick,
    onPlanetClick,
}: WorldMapViewProps) {
    const containerRef = useRef<HTMLDivElement | null>(null)
    const mapRef = useRef<WorldMap | null>(null)

    // Mirror every prop into a ref so we can read the freshest value from
    // inside the async init().then handler — which may run after this render
    // and any subsequent renders.
    const planetsRef = useRef(planets)
    const shipsRef = useRef(ships)
    const selectedShipIdRef = useRef(selectedShipId)
    const onTileClickRef = useRef(onTileClick)
    const onPlanetClickRef = useRef(onPlanetClick)
    useEffect(() => {
        planetsRef.current = planets
    }, [planets])
    useEffect(() => {
        shipsRef.current = ships
    }, [ships])
    useEffect(() => {
        selectedShipIdRef.current = selectedShipId
    }, [selectedShipId])
    useEffect(() => {
        onTileClickRef.current = onTileClick
    }, [onTileClick])
    useEffect(() => {
        onPlanetClickRef.current = onPlanetClick
    }, [onPlanetClick])

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
            map.setPlanets(planetsRef.current)
            map.setShips(shipsRef.current, selectedShipIdRef.current)
            map.setOnTileClick((x, y) => onTileClickRef.current?.(x, y))
            map.setOnPlanetClick((planet) => onPlanetClickRef.current?.(planet))
            mapRef.current = map
        })

        return () => {
            cancelled = true
            mapRef.current?.destroy()
            mapRef.current = null
        }
    }, [])

    // Subsequent updates: once mapRef is set, push through normally. The
    // first render's payload was already applied inside init().then above.
    useEffect(() => {
        mapRef.current?.setPlanets(planets)
    }, [planets])

    useEffect(() => {
        mapRef.current?.setShips(ships, selectedShipId)
    }, [ships, selectedShipId])

    return <div ref={containerRef} className="world-map" />
}
