import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelOrder, listOrders } from '../api/orders'
import type { ShipOrderDto } from '../types/api'

/** How often to refetch the queue. Matches the world tick poll cadence. */
const ORDERS_REFRESH_MS = 5000

export function OrdersPanel() {
    const queryClient = useQueryClient()

    const { data: orders, isLoading } = useQuery({
        queryKey: ['orders'],
        queryFn: ({ signal }) => listOrders(signal),
        refetchInterval: ORDERS_REFRESH_MS,
    })

    const cancel = useMutation({
        mutationFn: cancelOrder,
        // Refresh the queue immediately so the cancelled order disappears
        // without waiting for the next poll.
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['orders'] }),
    })

    if (isLoading) return <p>Loading orders…</p>
    if (!orders || orders.length === 0) {
        return (
            <p className="orders-empty">Your ship is idle. Click a planet to send it somewhere.</p>
        )
    }

    return (
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
                    >
                        Cancel
                    </button>
                </li>
            ))}
        </ul>
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
