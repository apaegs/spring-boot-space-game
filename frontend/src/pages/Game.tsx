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
 *   - Action-mode state ('idle' | 'targetingMove') and the map-click handler
 *     that closes the loop when the player picks a target.
 *
 * <p>Layout is full-viewport: header on top, map fills the left/center, right
 * sidebar holds the ship list and the selected-ship panel.
 */
export function Game() {
    const queryClient = useQueryClient()
    const { selectedShipId } = useSelectedShip()
    const [actionMode, setActionMode] = useState<'idle' | 'targetingMove'>('idle')

    const { data: ships } = useQuery({
        queryKey: ['ships'],
        queryFn: ({ signal }) => listMyShips(signal),
        refetchInterval: POLL_MS,
    })

    const { data: world } = useQuery({
        queryKey: ['world'],
        queryFn: ({ signal }) => getWorld(signal),
        refetchInterval: POLL_MS,
    })

    const { data: planets } = useQuery({
        queryKey: ['planets'],
        queryFn: ({ signal }) => listPlanets(signal),
        staleTime: Infinity,
    })

    const selectedShip = ships?.find((s) => s.id === selectedShipId) ?? null

    const move = useMutation({
        mutationFn: ({ shipId, x, y }: { shipId: string; x: number; y: number }) =>
            createOrder(shipId, { kind: 'MOVE', params: { x, y } }),
        onSettled: (_data, _error, variables) =>
            queryClient.invalidateQueries({ queryKey: ['orders', variables.shipId] }),
    })

    const onTileClick = (x: number, y: number) => {
        if (actionMode !== 'targetingMove') return
        if (!selectedShip) return
        move.mutate({ shipId: selectedShip.id, x, y })
        setActionMode('idle')
    }

    return (
        <div className={actionMode === 'targetingMove' ? 'game game--targeting' : 'game'}>
            <GameHeader tick={world?.currentTick} />

            <main className="game__main">
                <WorldMapView
                    planets={planets ?? []}
                    ships={ships ?? []}
                    selectedShipId={selectedShipId}
                    onTileClick={onTileClick}
                />
                {actionMode === 'targetingMove' && (
                    <div className="game__targeting-banner" role="status">
                        Click a tile to move {selectedShip?.name ?? 'your ship'} there.
                        <button type="button" onClick={() => setActionMode('idle')}>
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
                <ShipList ships={ships ?? []} />
                {selectedShip && (
                    <SelectedShipPanel
                        ship={selectedShip}
                        onPickMoveTarget={() => setActionMode('targetingMove')}
                    />
                )}
            </aside>
        </div>
    )
}
