import { useEffect, useRef } from 'react'
import { WorldMap } from '../pixi/WorldMap'
import type { PlanetDto, ShipDto } from '../types/api'

type WorldMapViewProps = {
    planets: PlanetDto[]
    ship: ShipDto | null
    onPlanetClick?: (planet: PlanetDto) => void
}

/**
 * React wrapper around the PixiJS {@link WorldMap}. Sole job: mount/unmount
 * the Pixi app on a div, and forward prop changes into the imperative API.
 * React never owns the canvas state — see CLAUDE.md "Frontend" section.
 */
export function WorldMapView({ planets, ship, onPlanetClick }: WorldMapViewProps) {
    const containerRef = useRef<HTMLDivElement | null>(null)
    const mapRef = useRef<WorldMap | null>(null)
    // Stash the latest callback in a ref so we can forward it without
    // remounting the entire Pixi app when the parent re-renders with a
    // new function reference. Updating the ref in an effect (not during
    // render) keeps React's strict-mode rules happy.
    const onPlanetClickRef = useRef(onPlanetClick)
    useEffect(() => {
        onPlanetClickRef.current = onPlanetClick
    }, [onPlanetClick])

    // Mount once. Pixi's init is async so we track cancellation to avoid
    // appending a stale canvas if the component unmounts during await.
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
        mapRef.current?.setShip(ship)
    }, [ship])

    return <div ref={containerRef} className="world-map" />
}
