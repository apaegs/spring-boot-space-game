import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { ApiError } from '../../api/client'
import { cancelOrder, createOrder, listOrders } from '../../api/orders'
import { renameShip } from '../../api/ship'
import type {
    CelestialBodyDto,
    ExtractMode,
    OrderKind,
    PublicShipDto,
    ResourceKind,
    ShipDto,
    ShipOrderDto,
} from '../../types/api'
import { ExtractDialog } from './ExtractDialog'
import { SellDialog } from './SellDialog'

/**
 * Bottom of the right sidebar — the "what did you just click?" surface.
 * Branches by the resolved selection:
 * <ul>
 *   <li><b>Own ship</b>: Info + Orders tabs. Same affordances as v1.</li>
 *   <li><b>Foreign ship</b>: Info only. No Orders tab — you can't queue
 *       actions for ships you don't own. Position + name are public per
 *       {@code PublicShipDto}.</li>
 *   <li><b>Body</b>: name, position, and the seeded description. PR 3 will
 *       grow this to render reserves + buy prices per body kind.</li>
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
    | {
          kind: 'ownShip'
          ship: ShipDto
          currentTick: number | undefined
          /** The body at the ship's current position, if any — null when in flight. Drives the Extract/Sell affordances. */
          currentBody: CelestialBodyDto | null
          onPickMoveTarget: () => void
      }
    | { kind: 'foreignShip'; ship: PublicShipDto }
    | { kind: 'body'; body: CelestialBodyDto }
    | { kind: 'none' }

const ORDERS_POLL_MS = 5000

export function SelectedEntityPanel(props: SelectedEntityPanelProps) {
    if (props.kind === 'none') {
        return (
            <section className="selected-ship-panel selected-ship-panel--empty">
                <p className="selected-ship-panel__empty">
                    Click a ship or body on the map to see details.
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

    if (props.kind === 'body') {
        const { body } = props
        return (
            <section className="selected-ship-panel">
                <header className="selected-ship-panel__header">
                    <h2>{body.name}</h2>
                    <span className="selected-ship-panel__badge">
                        {body.kind.toLowerCase().replace('_', ' ')}
                    </span>
                </header>
                <dl className="ship-info">
                    <dt>Position</dt>
                    <dd>
                        ({body.x}, {body.y})
                    </dd>
                    {body.description && (
                        <>
                            <dt>About</dt>
                            <dd>{body.description}</dd>
                        </>
                    )}
                </dl>

                {body.reserves.length > 0 && (
                    <div className="body-reserves">
                        <h3>Reserves</h3>
                        <ul>
                            {body.reserves.map((r) => (
                                <li key={r.kind}>
                                    <span className="body-reserves__kind">{r.kind}</span>
                                    <span className="body-reserves__amount">
                                        {r.reserve.toLocaleString('en-US')}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}

                {body.buyPrices.length > 0 && (
                    <div className="body-buyers">
                        <h3>Buys</h3>
                        <ul>
                            {body.buyPrices.map((p) => (
                                <li key={p.kind}>
                                    <span className="body-buyers__kind">{p.kind}</span>
                                    <span className="body-buyers__price">
                                        {p.pricePerUnit.toLocaleString('en-US')} cr/unit
                                    </span>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
            </section>
        )
    }

    return <OwnShipPanel {...props} />
}

// --- own-ship view: Info + Orders tabs ---

function OwnShipPanel({
    ship,
    currentTick,
    currentBody,
    onPickMoveTarget,
}: {
    ship: ShipDto
    currentTick: number | undefined
    currentBody: CelestialBodyDto | null
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
                <OwnShipOrders
                    ship={ship}
                    currentBody={currentBody}
                    onPickMoveTarget={onPickMoveTarget}
                />
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
    const cargoTotal = ship.cargo.reduce((sum, c) => sum + c.qty, 0)
    return (
        <>
            <dl className="ship-info">
                <dt>Class</dt>
                <dd>{ship.shipTypeName}</dd>
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

            <div className="cargo">
                <h3>
                    Cargo{' '}
                    <span className="cargo__total">
                        {cargoTotal.toLocaleString('en-US')} / {ship.cargoCapacity.toLocaleString('en-US')}
                    </span>
                </h3>
                {ship.cargo.length === 0 ? (
                    <p className="cargo__empty">Empty hold.</p>
                ) : (
                    <ul>
                        {ship.cargo.map((c) => (
                            <li key={c.resourceKind}>
                                <span className="cargo__kind">{c.resourceKind}</span>
                                <span className="cargo__qty">{c.qty.toLocaleString('en-US')}</span>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </>
    )
}

function OwnShipOrders({
    ship,
    currentBody,
    onPickMoveTarget,
}: {
    ship: ShipDto
    currentBody: CelestialBodyDto | null
    onPickMoveTarget: () => void
}) {
    const queryClient = useQueryClient()
    const [addOpen, setAddOpen] = useState(false)
    const [extractOpen, setExtractOpen] = useState(false)
    const [sellOpen, setSellOpen] = useState(false)

    const { data: orders, isLoading } = useQuery({
        queryKey: ['orders', ship.id],
        queryFn: ({ signal }) => listOrders(ship.id, signal),
        refetchInterval: ORDERS_POLL_MS,
        // Keep the previous list visible during the 5 s poll. Without this the
        // panel flashes empty for one render every cycle while react-query
        // swaps in the next result.
        placeholderData: keepPreviousData,
    })

    const cancel = useMutation({
        mutationFn: (orderId: string) => cancelOrder(ship.id, orderId),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })

    // LAND/TAKE_OFF post immediately — no extra UI. The auto-prereq middleware
    // also auto-queues LAND before EXTRACT/SELL if the ship isn't at a body.
    const queueLand = useMutation({
        mutationFn: () => createOrder(ship.id, { kind: 'LAND' }),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })
    const queueTakeOff = useMutation({
        mutationFn: () => createOrder(ship.id, { kind: 'TAKE_OFF' }),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })
    const queueExtract = useMutation({
        mutationFn: (params: { resourceKind: ResourceKind; mode: ExtractMode }) =>
            createOrder(ship.id, { kind: 'EXTRACT', params }),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })
    const queueSell = useMutation({
        mutationFn: (resourceKind: ResourceKind) =>
            createOrder(ship.id, { kind: 'SELL', params: { resourceKind } }),
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders', ship.id] }),
    })

    const pickAction = (kind: OrderKind) => {
        setAddOpen(false)
        if (kind === 'MOVE') {
            onPickMoveTarget()
        } else if (kind === 'LAND') {
            queueLand.mutate()
        } else if (kind === 'TAKE_OFF') {
            queueTakeOff.mutate()
        } else if (kind === 'EXTRACT') {
            setExtractOpen(true)
        } else if (kind === 'SELL') {
            setSellOpen(true)
        }
    }

    return (
        <div className="orders-view">
            {isLoading && (
                <ul className="orders-list orders-list--skeleton" aria-busy="true" aria-label="Loading orders">
                    {[0, 1, 2].map((i) => (
                        <li key={i} className="order-row order-row--skeleton" aria-hidden="true">
                            <span className="order-skeleton order-skeleton--kind" />
                            <span className="order-skeleton order-skeleton--status" />
                            <span className="order-skeleton order-skeleton--params" />
                        </li>
                    ))}
                </ul>
            )}
            {!isLoading && orders && orders.length === 0 && (
                <p className="orders-empty">
                    No orders queued. Use <strong>+ Add Order</strong> below to <em>Move</em> or
                    <em> Land</em>.
                </p>
            )}
            {orders && orders.length > 0 && (
                <ul className="orders-list">
                    {orders.map((order) => (
                        <li
                            key={order.id}
                            className={
                                order.autoInserted ? 'order-row order-row--auto' : 'order-row'
                            }
                        >
                            <span className={`order-kind order-kind--${order.kind.toLowerCase()}`}>
                                {order.kind}
                            </span>
                            <span className="order-status">{order.status.toLowerCase()}</span>
                            <span className="order-params">{describeParams(order)}</span>
                            {order.autoInserted && (
                                <span
                                    className="order-auto-badge"
                                    title="Auto-inserted as a prerequisite for the next order"
                                >
                                    ↩ auto
                                </span>
                            )}
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
                        <button type="button" onClick={() => pickAction('TAKE_OFF')}>
                            Take off
                        </button>
                        <button
                            type="button"
                            onClick={() => pickAction('EXTRACT')}
                            disabled={!currentBody}
                            title={currentBody ? undefined : 'Land on a body first'}
                        >
                            Extract
                        </button>
                        <button
                            type="button"
                            onClick={() => pickAction('SELL')}
                            disabled={!currentBody}
                            title={currentBody ? undefined : 'Land on a body first'}
                        >
                            Sell
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
            {queueTakeOff.error && (
                <p className="form-error" role="alert">
                    Could not take off:{' '}
                    {queueTakeOff.error instanceof ApiError
                        ? queueTakeOff.error.message
                        : 'unknown error'}
                </p>
            )}

            {currentBody && (
                <ExtractDialog
                    open={extractOpen}
                    body={currentBody}
                    onCancel={() => setExtractOpen(false)}
                    onConfirm={async (resourceKind, mode) => {
                        await queueExtract.mutateAsync({ resourceKind, mode })
                        setExtractOpen(false)
                    }}
                />
            )}
            {currentBody && (
                <SellDialog
                    open={sellOpen}
                    body={currentBody}
                    cargo={ship.cargo}
                    onCancel={() => setSellOpen(false)}
                    onConfirm={async (resourceKind) => {
                        await queueSell.mutateAsync(resourceKind)
                        setSellOpen(false)
                    }}
                />
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
    if (order.kind === 'EXTRACT') {
        const resource = order.params['resourceKind']
        const mode = order.params['mode']
        const modeText =
            typeof mode === 'string'
                ? mode === 'until_cancelled'
                    ? '∞'
                    : mode
                : isModeWithTicks(mode)
                  ? `${order.progressTicks}/${mode.ticks}t`
                  : isModeUntilFull(mode)
                    ? 'fill'
                    : ''
        return typeof resource === 'string' ? `${resource} · ${modeText}` : ''
    }
    if (order.kind === 'SELL') {
        const resource = order.params['resourceKind']
        return typeof resource === 'string' ? String(resource) : ''
    }
    return ''
}

function isModeWithTicks(v: unknown): v is { ticks: number } {
    return typeof v === 'object' && v !== null && typeof (v as { ticks?: unknown }).ticks === 'number'
}

function isModeUntilFull(v: unknown): v is { until_full: true } {
    return typeof v === 'object' && v !== null && (v as { until_full?: unknown }).until_full === true
}
