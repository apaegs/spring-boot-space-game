import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { getMe } from '../api/auth'
import { createOrder } from '../api/orders'
import { listBodies } from '../api/bodies'
import { listMyShips } from '../api/ship'
import { getWorld, listWorldShips } from '../api/world'
import { GameHeader } from '../components/game/GameHeader'
import {
    SelectedEntityPanel,
    type SelectedEntityPanelProps,
} from '../components/game/SelectedEntityPanel'
import { ShipList } from '../components/game/ShipList'
import { WorldMapView } from '../components/WorldMapView'
import type { ShipOnMap } from '../pixi/WorldMap'
import type { Selection } from '../selection/SelectionContext'
import { useSelection } from '../selection/SelectionContext'
import type { CelestialBodyDto, PublicShipDto, ShipDto } from '../types/api'

const POLL_MS = 5000

/**
 * Top-level game view. Owns:
 *   - Own-ship list polling (drives sidebar + own marker colors).
 *   - World-ship list polling (drives foreign ship rendering on the map).
 *   - World/tick polling (drives header counter and gameplay heartbeat).
 *   - Body list (static — staleTime: Infinity).
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
    const { selection, selectedShipId, setSelection } = useSelection()
    const [actionMode, setActionMode] = useState<ActionMode>({ type: 'idle' })

    const shipsQuery = useQuery({
        queryKey: ['ships'],
        queryFn: ({ signal }) => listMyShips(signal),
        refetchInterval: POLL_MS,
    })

    const worldShipsQuery = useQuery({
        queryKey: ['world-ships'],
        queryFn: ({ signal }) => listWorldShips(signal),
        refetchInterval: POLL_MS,
    })

    const worldQuery = useQuery({
        queryKey: ['world'],
        queryFn: ({ signal }) => getWorld(signal),
        refetchInterval: POLL_MS,
    })

    const bodiesQuery = useQuery({
        queryKey: ['bodies'],
        queryFn: ({ signal }) => listBodies(signal),
        // Reserves/prices change as players extract/sell — refresh on the same
        // cadence as ships so the body panel doesn't drift.
        refetchInterval: POLL_MS,
    })

    // Credits live on the user. AuthProvider does a one-shot getMe at mount;
    // SELL completions don't push, so we poll here to keep the header's credit
    // readout fresh. Same cadence as the other game-loop queries.
    const meQuery = useQuery({
        queryKey: ['me-credits'],
        queryFn: ({ signal }) => getMe(signal),
        refetchInterval: POLL_MS,
    })

    // Stabilize the array references so {@code useMemo} below treats the
    // empty-state fallback as stable across renders. Without this, the
    // `?? []` would mint a fresh array each render and bust the memo.
    const ships = useMemo(() => shipsQuery.data ?? [], [shipsQuery.data])
    const world = worldQuery.data
    const bodies = bodiesQuery.data ?? []
    const selectedShip = ships.find((s) => s.id === selectedShipId) ?? null

    // Build the on-map ship list by merging the two ship sources. Own ships
    // overlay world ships so the local data (which is optimistically updated
    // on creation and refetched after a move) wins over the world poll. The
    // earlier "branch entirely to world data once defined" approach had a bug:
    // a ship created via `+ New ship` was added to the `['ships']` cache
    // immediately but didn't appear on the world list until the next 5s
    // poll — the new ship's marker was missing for up to a tick.
    const shipsOnMap: ShipOnMap[] = useMemo(() => {
        const byId = new Map<string, ShipOnMap>()
        for (const s of worldShipsQuery.data ?? []) {
            byId.set(s.id, { id: s.id, name: s.name, x: s.x, y: s.y, isOwn: false })
        }
        for (const s of ships) {
            byId.set(s.id, { id: s.id, name: s.name, x: s.x, y: s.y, isOwn: true })
        }
        return [...byId.values()]
    }, [ships, worldShipsQuery.data])

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
        if (actionMode.type === 'targetingMove') {
            if (actionMode.shipId !== selectedShipId) {
                // Player switched ships after entering targeting mode. Cancel
                // the stale targeting state silently — they need to re-pick
                // Move for the new selection if they still want to.
                setActionMode({ type: 'idle' })
                return
            }
            move.mutate({ shipId: actionMode.shipId, x, y })
            setActionMode({ type: 'idle' })
            return
        }
        // Normal mode: left-click on empty space is a no-op. Deselection lives
        // on the right mouse button — see {@link onRightClick}.
    }

    /**
     * Right-mouse-button anywhere on the map. RTS-style "cancel / deselect":
     * during targeting it aborts the action; otherwise it clears the current
     * selection. The Pixi layer suppresses the browser context menu, so this
     * is the only effect a right press has.
     */
    const onRightClick = () => {
        if (actionMode.type === 'targetingMove') {
            setActionMode({ type: 'idle' })
            return
        }
        setSelection(null)
    }

    // Ship and body clicks only select in normal mode. WorldMap stops
    // firing these callbacks in targeting mode (markers become transparent
    // to pointer events), so we don't need a runtime guard here — but the
    // defensive check costs nothing and keeps intent explicit. Deselection
    // lives on the right mouse button alone; clicking an already-selected
    // marker is a no-op so left-click only ever <i>selects</i>.
    const onShipClick = (ship: ShipOnMap) => {
        if (isTargetingActive) return
        setSelection({ kind: 'ship', id: ship.id })
    }

    const onBodyClick = (bodyId: string) => {
        if (isTargetingActive) return
        setSelection({ kind: 'body', id: bodyId })
    }

    const startMoveTargeting = () => {
        if (!selectedShip) return
        setActionMode({ type: 'targetingMove', shipId: selectedShip.id })
    }

    // Surface any of the four root queries failing. Without this, a backend
    // hiccup just shows an empty map + "Loading…" sidebar forever — the player
    // can't tell whether it's a slow first load or a real problem.
    const queryError =
        shipsQuery.error ?? worldQuery.error ?? worldShipsQuery.error ?? bodiesQuery.error

    const retryAll = () => {
        void shipsQuery.refetch()
        void worldShipsQuery.refetch()
        void worldQuery.refetch()
        void bodiesQuery.refetch()
    }

    return (
        <div className={isTargetingActive ? 'game game--targeting' : 'game'}>
            <GameHeader tick={world?.currentTick} credits={meQuery.data?.credits} />

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
                    bodies={bodies}
                    ships={shipsOnMap}
                    selection={selection}
                    isTargeting={isTargetingActive}
                    onTileClick={onTileClick}
                    onShipClick={onShipClick}
                    onBodyClick={(p) => onBodyClick(p.id)}
                    onRightClick={onRightClick}
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
                <SelectedEntityPanel
                    {...resolveSelectedEntity({
                        selection,
                        ownShips: ships,
                        worldShips: worldShipsQuery.data,
                        bodies,
                        currentTick: world?.currentTick,
                        currentBody: bodyAtSelectedShip(selectedShip, bodies),
                        onPickMoveTarget: startMoveTargeting,
                    })}
                />
            </aside>
        </div>
    )
}

/**
 * Resolve the current {@link Selection} against the data we have on hand,
 * producing the discriminated props for {@link SelectedEntityPanel}.
 *
 * <p>Foreign-ship lookups try the world-ships query first; if that hasn't
 * loaded yet (or the ship has vanished between renders) we fall through
 * to the empty state rather than rendering a stale row. Same fallback for
 * bodies and own ships — selection ids are opaque, the entities can
 * legitimately disappear (deletion, race with a refetch).
 */
function resolveSelectedEntity(input: {
    selection: Selection
    ownShips: ShipDto[]
    worldShips: PublicShipDto[] | undefined
    bodies: CelestialBodyDto[]
    currentTick: number | undefined
    /** The body the selected own-ship is currently at, if any. Pre-resolved by the caller from the ship's (x,y). */
    currentBody: CelestialBodyDto | null
    onPickMoveTarget: () => void
}): SelectedEntityPanelProps {
    const { selection, ownShips, worldShips, bodies, currentTick, currentBody, onPickMoveTarget } =
        input
    if (!selection) return { kind: 'none' }

    if (selection.kind === 'ship') {
        const own = ownShips.find((s) => s.id === selection.id)
        if (own) return { kind: 'ownShip', ship: own, currentTick, currentBody, onPickMoveTarget }
        const foreign = worldShips?.find((s) => s.id === selection.id)
        if (foreign) return { kind: 'foreignShip', ship: foreign }
        return { kind: 'none' }
    }

    const body = bodies.find((p) => p.id === selection.id)
    return body ? { kind: 'body', body } : { kind: 'none' }
}

/**
 * Returns the body that occupies the ship's tile, but only when the ship's
 * derived status says it's actually docked there. A ship drifting onto a
 * body's tile without LAND-ing isn't "at the body" — the EXTRACT/SELL
 * handlers would cancel anyway, so the UI shouldn't tempt the player with
 * those affordances.
 */
function bodyAtSelectedShip(
    ship: ShipDto | null,
    bodies: CelestialBodyDto[]
): CelestialBodyDto | null {
    if (!ship) return null
    if (ship.status !== 'LANDED' && ship.status !== 'ORBITING') return null
    return bodies.find((b) => b.x === ship.x && b.y === ship.y) ?? null
}
