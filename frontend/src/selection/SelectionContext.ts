import { createContext, useContext } from 'react'

/**
 * What the player has currently selected on the map / in the sidebar. The
 * selection can be a ship (their own or another player's), a celestial body
 * (planet, asteroid, gas giant, star), or nothing. The {@code kind} tag lets
 * consumers branch without type assertions.
 *
 * <p>Selection ids are opaque server IDs — the consumer fetches the actual
 * entity from its own data source (own ships from `useQuery('ships')`,
 * bodies from `useQuery('bodies')`, foreign ships from
 * `useQuery('world-ships')`).
 */
export type Selection =
    | { kind: 'ship'; id: string }
    | { kind: 'body'; id: string }
    | null

export type SelectionState = {
    selection: Selection
    setSelection: (selection: Selection) => void
    /**
     * Convenience derived value: the selected ship id when the selection is a
     * ship, otherwise null. Lets callsites that only care about ships skip the
     * kind-narrowing dance. Computed in the provider, not here.
     */
    selectedShipId: string | null
}

// Lives in a .ts (no JSX) file so the react-refresh ESLint rule stays happy —
// same pattern as the rest of the contexts in this app.
export const SelectionContext = createContext<SelectionState | null>(null)

export function useSelection(): SelectionState {
    const ctx = useContext(SelectionContext)
    if (!ctx) {
        throw new Error('useSelection must be used inside <SelectionProvider>')
    }
    return ctx
}
