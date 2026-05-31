import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listMyShips } from '../api/ship'
import { SelectedShipContext } from './SelectedShipContext'

/**
 * Holds the currently-selected ship for the whole game view. Wraps everything
 * under {@code /} so the right sidebar and the map both react to the same
 * selection state without prop-drilling.
 *
 * <p>Selection is *derived* during render rather than synchronized in an
 * effect: until the user has explicitly clicked a ship (or the new-ship
 * mutation programmatically selected one), we fall back to the first ship in
 * the list. Avoids the React-19 "setState in useEffect" anti-pattern.
 */
export function SelectedShipProvider({ children }: { children: ReactNode }) {
    const [explicit, setExplicit] = useState<string | null>(null)
    const [userPicked, setUserPicked] = useState(false)

    // Same queryKey as the rest of the app → cache is shared, no double fetch.
    const { data: ships } = useQuery({
        queryKey: ['ships'],
        queryFn: ({ signal }) => listMyShips(signal),
    })

    // Derived: user's explicit choice if they've made one, otherwise the first
    // ship (the auto-created mothership for fresh users, the oldest ship for
    // returning users). null only when ships hasn't loaded yet.
    const selectedShipId: string | null = userPicked
        ? explicit
        : ships && ships.length > 0
          ? ships[0].id
          : null

    const setSelectedShipId = (id: string | null) => {
        setExplicit(id)
        setUserPicked(true)
    }

    return (
        <SelectedShipContext.Provider value={{ selectedShipId, setSelectedShipId }}>
            {children}
        </SelectedShipContext.Provider>
    )
}
