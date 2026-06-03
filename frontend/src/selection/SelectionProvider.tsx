import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listMyShips } from '../api/ship'
import { SelectionContext, type Selection } from './SelectionContext'

/**
 * Holds the player's current selection (a ship, a celestial body, or nothing) for the
 * whole game view. Wraps everything under {@code /} so the right sidebar and
 * the map both react to the same state without prop-drilling.
 *
 * <p>Selection is *derived* during render rather than synchronized in an
 * effect: until the player has explicitly picked something, we fall back to
 * the first ship in their fleet — the auto-created mothership for fresh
 * users, the oldest ship for returning users. Avoids the React-19 "setState
 * in useEffect" anti-pattern.
 */
export function SelectionProvider({ children }: { children: ReactNode }) {
    const [explicit, setExplicit] = useState<Selection>(null)
    const [userPicked, setUserPicked] = useState(false)

    // Same queryKey as the rest of the app → cache is shared, no double fetch.
    const { data: ships } = useQuery({
        queryKey: ['ships'],
        queryFn: ({ signal }) => listMyShips(signal),
    })

    // Default to the first ship until the user picks something. Once they do
    // (even if they pick null to deselect), respect their choice.
    const selection: Selection = userPicked
        ? explicit
        : ships && ships.length > 0
          ? { kind: 'ship', id: ships[0].id }
          : null

    const setSelection = (next: Selection) => {
        setExplicit(next)
        setUserPicked(true)
    }

    const selectedShipId = selection?.kind === 'ship' ? selection.id : null

    return (
        <SelectionContext.Provider value={{ selection, setSelection, selectedShipId }}>
            {children}
        </SelectionContext.Provider>
    )
}
