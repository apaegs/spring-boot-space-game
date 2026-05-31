import { useEffect, useRef } from 'react'
import { WorldMap } from '../pixi/WorldMap'
import type { PlanetDto, ShipDto } from '../types/api'

type WorldMapViewProps = {
    planets: PlanetDto[]
    ship: ShipDto | null
}

/**
 * React wrapper around the PixiJS {@link WorldMap}. Sole job: mount/unmount
 * the Pixi app on a div, and forward prop changes into the imperative API.
 * React never owns the canvas state — see CLAUDE.md "Frontend" section.
 */
export function WorldMapView({ planets, ship }: WorldMapViewProps) {
    const containerRef = useRef<HTMLDivElement | null>(null)
    const mapRef = useRef<WorldMap | null>(null)

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
            mapRef.current = map
        })

        return () => {
            cancelled = true
            mapRef.current?.destroy()
            mapRef.current = null
        }
    }, [])

    // Forward planet updates whenever the prop changes.
    useEffect(() => {
        mapRef.current?.setPlanets(planets)
    }, [planets])

    // Forward ship updates.
    useEffect(() => {
        mapRef.current?.setShip(ship)
    }, [ship])

    return <div ref={containerRef} className="world-map" />
}
