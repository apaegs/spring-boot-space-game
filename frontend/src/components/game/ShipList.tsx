import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { createShip } from '../../api/ship'
import { useSelectedShip } from '../../ship/SelectedShipContext'
import type { ShipDto } from '../../types/api'

type ShipListProps = {
    ships: ShipDto[]
    /** True only while the very first fetch is in flight. Lets us show
     * "Loading…" instead of the false "no ships yet" empty state. */
    isLoading: boolean
}

/**
 * The player's fleet. Click a row to select that ship (drives the panel below
 * and the main view's camera). `+ New ship` POSTs to `/api/ships`, refreshes
 * the list, and auto-selects the new ship.
 */
export function ShipList({ ships, isLoading }: ShipListProps) {
    const { selectedShipId, setSelectedShipId } = useSelectedShip()
    const queryClient = useQueryClient()

    const create = useMutation({
        mutationFn: () => createShip(),
        onSuccess: (newShip) => {
            // Optimistically add to the cache so the new row appears immediately;
            // the next poll fills in any server-side fields we didn't predict.
            queryClient.setQueryData<ShipDto[]>(['ships'], (current) =>
                current ? [...current, newShip] : [newShip]
            )
            setSelectedShipId(newShip.id)
        },
    })

    return (
        <section className="ship-list">
            <header className="ship-list__header">
                <h2>Ships</h2>
                <button
                    type="button"
                    className="ship-list__add"
                    onClick={() => create.mutate()}
                    disabled={create.isPending}
                    title="Create a new ship"
                >
                    + New
                </button>
            </header>

            {ships.length === 0 && isLoading && <p className="ship-list__empty">Loading…</p>}
            {ships.length === 0 && !isLoading && (
                <p className="ship-list__empty">No ships yet. Click "+ New" to create one.</p>
            )}
            {ships.length > 0 && (
                <ul className="ship-list__items">
                    {ships.map((ship) => {
                        const isSelected = ship.id === selectedShipId
                        return (
                            <li key={ship.id}>
                                <button
                                    type="button"
                                    className={
                                        isSelected
                                            ? 'ship-list__item ship-list__item--selected'
                                            : 'ship-list__item'
                                    }
                                    onClick={() => setSelectedShipId(ship.id)}
                                >
                                    <span className="ship-list__name">{ship.name}</span>
                                    <span className="ship-list__pos">
                                        ({ship.x}, {ship.y})
                                    </span>
                                </button>
                            </li>
                        )
                    })}
                </ul>
            )}

            {create.error && (
                <p className="form-error" role="alert">
                    Could not create ship:{' '}
                    {create.error instanceof ApiError ? create.error.message : 'unknown error'}
                </p>
            )}
        </section>
    )
}
