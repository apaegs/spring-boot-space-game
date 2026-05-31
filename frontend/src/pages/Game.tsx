import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { createOrder } from '../api/orders'
import { listPlanets } from '../api/planets'
import { listMyShips } from '../api/ship'
import { getWorld } from '../api/world'
import { GameHeader } from '../components/game/GameHeader'
import { SelectedShipPanel } from '../components/game/SelectedShipPanel'
import { ShipList } from '../components/game/ShipList'
import { WorldMapView } from '../components/WorldMapView'
import { useSelectedShip } from '../ship/SelectedShipContext'

const POLL_MS = 5000

/**
 * Top-level game view. Owns:
 *   - Ship list polling (drives selection options + camera target).
 *   - World/tick polling (drives header counter and gameplay heartbeat).
 *   - Planet list (static — staleTime: Infinity).
 *   - Action-mode state and the map-click handler that closes the loop
 *     when the player picks a target.
 *
 * <p>Action mode is a tagged union: when targeting, it pins the {@code shipId}
 * the action was started against. If the player switches selection mid-target,
 * the targeting visually de-activates (banner hides, cursor returns to normal)
 * because we derive {@code isTargetingActive} from "actionMode shipId matches
 * the selection". A click on the map at that point also clears state. Avoids
 * the React-19 "setState in useEffect" anti-pattern.
 */
type ActionMode = { type: 'idle' } | { type: 'targetingMove'; shipId: string }

export function Game() {
    const queryClient = useQueryClient()
    const { selectedShipId } = useSelectedShip()
    const [actionMode, setActionMode] = useState<ActionMode>({ type: 'idle' })

    const shipsQuery = useQuery({
        queryKey: ['ships'],
        queryFn: ({ signal }) => listMyShips(signal),
        refetchInterval: POLL_MS,
    })

    const worldQuery = useQuery({
        queryKey: ['world'],
        queryFn: ({ signal }) => getWorld(signal),
        refetchInterval: POLL_MS,
    })

    const planetsQuery = useQuery({
        queryKey: ['planets'],
        queryFn: ({ signal }) => listPlanets(signal),
        staleTime: Infinity,
    })

    const ships = shipsQuery.data ?? []
    const world = worldQuery.data
    const planets = planetsQuery.data ?? []
    const selectedShip = ships.find((s) => s.id === selectedShipId) ?? null

    // Targeting "follows" selection: if the player switches ships mid-target,
    // the targeting visually de-activates immediately. State stays put for one
    // more interaction (handled by onTileClick / startMoveTargeting below)
    // rather than racing setState in an effect.
    const isTargetingActive =
        actionMode.type === 'targetingMove' && actionMode.shipId === selectedShipId

    const move = useMutation({
        mutationFn: ({ shipId, x, y }: { shipId: string; x: number; y: number }) =>
            createOrder(shipId, { kind: 'MOVE', params: { x, y } }),
        onSettled: (_data, _error, variables) =>
            queryClient.invalidateQueries({ queryKey: ['orders', variables.shipId] }),
    })

    const onTileClick = (x: number, y: number) => {
        if (actionMode.type !== 'targetingMove') return
        if (actionMode.shipId !== selectedShipId) {
            // Player switched ships after entering targeting mode. Cancel the
            // stale targeting state silently — they need to re-pick Move for
            // the new selection if they still want to.
            setActionMode({ type: 'idle' })
            return
        }
        move.mutate({ shipId: actionMode.shipId, x, y })
        setActionMode({ type: 'idle' })
    }

    const startMoveTargeting = () => {
        if (!selectedShip) return
        setActionMode({ type: 'targetingMove', shipId: selectedShip.id })
    }

    // Surface any of the three root queries failing. Without this, a backend
    // hiccup just shows an empty map + "Loading…" sidebar forever — the player
    // can't tell whether it's a slow first load or a real problem.
    const queryError = shipsQuery.error ?? worldQuery.error ?? planetsQuery.error

    const retryAll = () => {
        void shipsQuery.refetch()
        void worldQuery.refetch()
        void planetsQuery.refetch()
    }

    return (
        <div className={isTargetingActive ? 'game game--targeting' : 'game'}>
            <GameHeader tick={world?.currentTick} />

            <main className="game__main">
                {queryError && (
                    <div className="game__query-error" role="alert">
                        <strong>Couldn't reach the backend.</strong>{' '}
                        {queryError instanceof ApiError ? queryError.message : 'Network error.'}
                        <button type="button" onClick={retryAll}>
                            Retry
                        </button>
                    </div>
                )}

                <WorldMapView
                    planets={planets}
                    ships={ships}
                    selectedShipId={selectedShipId}
                    onTileClick={onTileClick}
                />

                {isTargetingActive && (
                    <div className="game__targeting-banner" role="status">
                        Click a tile to move {selectedShip?.name ?? 'your ship'} there.
                        <button type="button" onClick={() => setActionMode({ type: 'idle' })}>
                            Cancel
                        </button>
                    </div>
                )}

                {move.error && (
                    <p className="form-error game__error" role="alert">
                        Could not queue move:{' '}
                        {move.error instanceof ApiError ? move.error.message : 'unknown error'}
                    </p>
                )}
            </main>

            <aside className="game__sidebar">
                <ShipList ships={ships} isLoading={shipsQuery.isPending} />
                {selectedShip && (
                    <SelectedShipPanel
                        ship={selectedShip}
                        currentTick={world?.currentTick}
                        onPickMoveTarget={startMoveTargeting}
                    />
                )}
            </aside>
        </div>
    )
}
