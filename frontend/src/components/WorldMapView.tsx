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

    // Stash the latest callbacks in refs so we forward them without forcing
    // the Pixi app to remount when the parent re-renders with new functions.
    // Updated in an effect (not during render) to keep React 19 strict-mode
    // happy.
    const onTileClickRef = useRef(onTileClick)
    const onPlanetClickRef = useRef(onPlanetClick)
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

    useEffect(() => {
        mapRef.current?.setPlanets(planets)
    }, [planets])

    useEffect(() => {
        mapRef.current?.setShips(ships, selectedShipId)
    }, [ships, selectedShipId])

    return <div ref={containerRef} className="world-map" />
}
