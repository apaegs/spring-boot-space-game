import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { cancelOrder, createOrder, listOrders } from '../../api/orders'
import type { OrderKind, ShipDto, ShipOrderDto } from '../../types/api'

/**
 * Bottom of the right sidebar. Shows the selected ship's info by default; the
 * "Orders" button swaps it to the queue + Add Order view.
 *
 * <p>Add Order opens an action-type menu. Picking MOVE flips the parent into
 * targeting mode (via the {@code onPickMoveTarget} prop); picking LAND posts
 * the order immediately (no targeting needed — LAND uses the ship's current
 * position).
 */
type SelectedShipPanelProps = {
    ship: ShipDto
    /** Current world tick — surfaced in the info view so the panel doesn't
     * feel "frozen" when the header is partially scrolled / not in view. */
    currentTick: number | undefined
    /** Called when the player picks "Move" from the action menu. Parent then
     * enables targeting mode on the map. */
    onPickMoveTarget: () => void
}

const ORDERS_POLL_MS = 5000

export function SelectedShipPanel({ ship, currentTick, onPickMoveTarget }: SelectedShipPanelProps) {
    const [view, setView] = useState<'info' | 'orders'>('info')

    return (
        <section className="selected-ship-panel">
            <header className="selected-ship-panel__header">
                <h2>{ship.name}</h2>
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

            {view === 'info' && <InfoView ship={ship} currentTick={currentTick} />}
            {view === 'orders' && <OrdersView ship={ship} onPickMoveTarget={onPickMoveTarget} />}
        </section>
    )
}

function InfoView({ ship, currentTick }: { ship: ShipDto; currentTick: number | undefined }) {
    return (
        <dl className="ship-info">
            <dt>Position</dt>
            <dd>
                ({ship.x}, {ship.y})
            </dd>
            <dt>World tick</dt>
            <dd>{currentTick ?? '—'}</dd>
            <dt>Created</dt>
            <dd>{new Date(ship.createdAt).toLocaleDateString()}</dd>
        </dl>
    )
}

function OrdersView({ ship, onPickMoveTarget }: { ship: ShipDto; onPickMoveTarget: () => void }) {
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
