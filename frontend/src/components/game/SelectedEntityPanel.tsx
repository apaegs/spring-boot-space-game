import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { ApiError } from '../../api/client'
import { cancelOrder, createOrder, listOrders } from '../../api/orders'
import { renameShip } from '../../api/ship'
import type {
    OrderKind,
    PlanetDto,
    PublicShipDto,
    ShipDto,
    ShipOrderDto,
} from '../../types/api'

/**
 * Bottom of the right sidebar — the "what did you just click?" surface.
 * Branches by the resolved selection:
 * <ul>
 *   <li><b>Own ship</b>: Info + Orders tabs. Same affordances as v1.</li>
 *   <li><b>Foreign ship</b>: Info only. No Orders tab — you can't queue
 *       actions for ships you don't own. Position + name are public per
 *       {@code PublicShipDto}.</li>
 *   <li><b>Planet</b>: name, position, and the seeded description.</li>
 *   <li><b>None</b>: a soft placeholder so the panel doesn't just vanish.
 *       Hiding it was the alternative, but a stable layout reads better
 *       than a jumping sidebar.</li>
 * </ul>
 *
 * <p>Parent (Game.tsx) resolves which entity is selected and discriminates
 * via the {@code selection} prop. This component does not fetch — every
 * data source it needs is handed in.
 */
export type SelectedEntityPanelProps =
    | { kind: 'ownShip'; ship: ShipDto; currentTick: number | undefined; onPickMoveTarget: () => void }
    | { kind: 'foreignShip'; ship: PublicShipDto }
    | { kind: 'planet'; planet: PlanetDto }
    | { kind: 'none' }

const ORDERS_POLL_MS = 5000

export function SelectedEntityPanel(props: SelectedEntityPanelProps) {
    if (props.kind === 'none') {
        return (
            <section className="selected-ship-panel selected-ship-panel--empty">
                <p className="selected-ship-panel__empty">
                    Click a ship or planet on the map to see details.
                </p>
            </section>
        )
    }

    if (props.kind === 'foreignShip') {
        return (
            <section className="selected-ship-panel">
                <header className="selected-ship-panel__header">
                    <h2>{props.ship.name}</h2>
                    <span className="selected-ship-panel__badge">other player</span>
                </header>
                <dl className="ship-info">
                    <dt>Position</dt>
                    <dd>
                        ({props.ship.x}, {props.ship.y})
                    </dd>
                </dl>
            </section>
        )
    }

    if (props.kind === 'planet') {
        return (
            <section className="selected-ship-panel">
                <header className="selected-ship-panel__header">
                    <h2>{props.planet.name}</h2>
                    <span className="selected-ship-panel__badge">planet</span>
                </header>
                <dl className="ship-info">
                    <dt>Position</dt>
                    <dd>
                        ({props.planet.x}, {props.planet.y})
                    </dd>
                    {props.planet.description && (
                        <>
                            <dt>About</dt>
                            <dd>{props.planet.description}</dd>
                        </>
                    )}
                </dl>
            </section>
        )
    }

    return <OwnShipPanel {...props} />
}

// --- own-ship view: Info + Orders tabs ---

function OwnShipPanel({
    ship,
    currentTick,
    onPickMoveTarget,
}: {
    ship: ShipDto
    currentTick: number | undefined
    onPickMoveTarget: () => void
}) {
    const [view, setView] = useState<'info' | 'orders'>('info')

    return (
        <section className="selected-ship-panel">
            <header className="selected-ship-panel__header">
                <ShipNameEditor ship={ship} />
                <nav className="selected-ship-panel__tabs">
                    <button
                        type="button"
                        className={view === 'info' ? 'tab tab--active' : 'tab'}
                        onClick={() => setView('info')}
                    >
                        Info
                    </button>
                    <button
                        type="button"
                        className={view === 'orders' ? 'tab tab--active' : 'tab'}
                        onClick={() => setView('orders')}
                    >
                        Orders
                    </button>
                </nav>
            </header>

            {view === 'info' && <OwnShipInfo ship={ship} currentTick={currentTick} />}
            {view === 'orders' && (
                <OwnShipOrders ship={ship} onPickMoveTarget={onPickMoveTarget} />
            )}
        </section>
    )
}

function ShipNameEditor({ ship }: { ship: ShipDto }) {
    const queryClient = useQueryClient()
    const [editing, setEditing] = useState(false)
    const [draft, setDraft] = useState('')
    const inputRef = useRef<HTMLInputElement>(null)

    const rename = useMutation({
        mutationFn: (name: string) => renameShip(ship.id, name),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['ships'] })
            setEditing(false)
        },
    })

    const startEditing = () => {
        setDraft(ship.name)
        setEditing(true)
        // Focus after React renders the input.
        setTimeout(() => inputRef.current?.select(), 0)
    }

    const commit = () => {
        const trimmed = draft.trim()
        if (trimmed && trimmed !== ship.name) {
            rename.mutate(trimmed)
        } else {
            setEditing(false)
        }
    }

    if (editing) {
        return (
            <div className="ship-name-editor">
                <input
                    ref={inputRef}
                    className="ship-name-editor__input"
                    value={draft}
                    maxLength={64}
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') commit()
                        if (e.key === 'Escape') setEditing(false)
                    }}
                    onBlur={commit}
                    aria-label="Ship name"
                />
                {rename.error && (
                    <p className="form-error" role="alert">
                        {rename.error instanceof ApiError
                            ? rename.error.message
                            : 'Could not rename'}
                    </p>
                )}
            </div>
        )
    }

    return (
        <div className="ship-name-editor">
            <h2>{ship.name}</h2>
            <button
                type="button"
                className="ship-name-editor__edit-btn"
                onClick={startEditing}
                title="Rename ship"
                aria-label="Rename ship"
            >
                ✎
            </button>
        </div>
    )
}

function OwnShipInfo({ ship, currentTick }: { ship: ShipDto; currentTick: number | undefined }) {
    return (
        <dl className="ship-info">
            <dt>Position</dt>
            <dd>
                ({ship.x}, {ship.y})
            </dd>
            <dt>Status</dt>
            <dd>{ship.status.charAt(0) + ship.status.slice(1).toLowerCase()}</dd>
            <dt>World tick</dt>
            <dd>{currentTick ?? '—'}</dd>
            <dt>Created</dt>
            <dd>{new Date(ship.createdAt).toLocaleDateString()}</dd>
        </dl>
    )
}

function OwnShipOrders({
    ship,
    onPickMoveTarget,
}: {
    ship: ShipDto
    onPickMoveTarget: () => void
}) {
    const queryClient = useQueryClient()
    const [addOpen, setAddOpen] = useState(false)

    const { data: orders, isLoading } = useQuery({
        queryKey: ['orders', ship.id],
        queryFn: ({ signal }) => listOrders(ship.id, signal),
        refetchInterval: ORDERS_POLL_MS,
    })

    const cancel = useMutation({
        mutationFn: (orderId: string) => cancelOrder(ship.id, orderId),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })

    // LAND posts immediately — no targeting needed (lands on the current tile).
    const queueLand = useMutation({
        mutationFn: () => createOrder(ship.id, { kind: 'LAND' }),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })

    const pickAction = (kind: OrderKind) => {
        setAddOpen(false)
        if (kind === 'MOVE') {
            onPickMoveTarget()
        } else if (kind === 'LAND') {
            queueLand.mutate()
        }
    }

    return (
        <div className="orders-view">
            {isLoading && <p>Loading orders…</p>}
            {orders && orders.length === 0 && (
                <p className="orders-empty">Queue is empty. Add an order below.</p>
            )}
            {orders && orders.length > 0 && (
                <ul className="orders-list">
                    {orders.map((order) => (
                        <li key={order.id} className="order-row">
                            <span className={`order-kind order-kind--${order.kind.toLowerCase()}`}>
                                {order.kind}
                            </span>
                            <span className="order-status">{order.status.toLowerCase()}</span>
                            <span className="order-params">{describeParams(order)}</span>
                            <button
                                type="button"
                                onClick={() => cancel.mutate(order.id)}
                                disabled={cancel.isPending}
                                title="Cancel this order"
                            >
                                ×
                            </button>
                        </li>
                    ))}
                </ul>
            )}

            <div className="add-order">
                {!addOpen && (
                    <button
                        type="button"
                        className="add-order__btn"
                        onClick={() => setAddOpen(true)}
                    >
                        + Add Order
                    </button>
                )}
                {addOpen && (
                    <div className="add-order__menu">
                        <button type="button" onClick={() => pickAction('MOVE')}>
                            Move
                        </button>
                        <button type="button" onClick={() => pickAction('LAND')}>
                            Land
                        </button>
                        <button
                            type="button"
                            className="add-order__cancel"
                            onClick={() => setAddOpen(false)}
                        >
                            Cancel
                        </button>
                    </div>
                )}
            </div>

            {queueLand.error && (
                <p className="form-error" role="alert">
                    Could not land:{' '}
                    {queueLand.error instanceof ApiError
                        ? queueLand.error.message
                        : 'unknown error'}
                </p>
            )}
        </div>
    )
}

function describeParams(order: ShipOrderDto): string {
    if (order.kind === 'MOVE') {
        const x = order.params['x']
        const y = order.params['y']
        if (typeof x === 'number' && typeof y === 'number') {
            return `→ (${x}, ${y})`
        }
    }
    return ''
}
