import { createContext, useContext } from 'react'

export type SelectedShipState = {
    /** ID of the currently selected ship, or null if none. */
    selectedShipId: string | null
    /** Programmatically change selection (e.g. from the ship list). */
    setSelectedShipId: (id: string | null) => void
}

// Lives in a .ts (no JSX) file so the react-refresh ESLint rule stays happy —
// see CLAUDE.md "Integration test setup" comment for the same pattern in auth.
export const SelectedShipContext = createContext<SelectedShipState | null>(null)

export function useSelectedShip(): SelectedShipState {
    const ctx = useContext(SelectedShipContext)
    if (!ctx) {
        throw new Error('useSelectedShip must be used inside <SelectedShipProvider>')
    }
    return ctx
}
