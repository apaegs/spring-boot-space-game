import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { createOrder } from '../api/orders'
import { listPlanets } from '../api/planets'
import { getMyShip } from '../api/ship'
import { getWorld } from '../api/world'
import { useAuth } from '../auth/AuthContext'
import { OrdersPanel } from '../components/OrdersPanel'
import { TickCounter } from '../components/TickCounter'
import { WorldMapView } from '../components/WorldMapView'
import { ApiError } from '../api/client'
import type { PlanetDto } from '../types/api'

/** Refetch ship + world this often so movement / ticks visibly advance. */
const POLL_MS = 5000

export function Dashboard() {
    const { user, logout } = useAuth()
    const navigate = useNavigate()
    const queryClient = useQueryClient()

    const { data: ship } = useQuery({
        queryKey: ['ship'],
        queryFn: ({ signal }) => getMyShip(signal),
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

    // Clicking a planet enqueues MOVE then LAND. The two orders are sent
    // sequentially so the LAND can't slip ahead of the MOVE in any race.
    // The orders endpoint orders strictly by created_at so even if we
    // fired them in parallel they'd land FIFO — sequential is the explicit
    // safer pattern.
    const travelTo = useMutation({
        mutationFn: async (planet: PlanetDto) => {
            await createOrder({ kind: 'MOVE', params: { x: planet.x, y: planet.y } })
            await createOrder({ kind: 'LAND' })
        },
        // onSettled (not onSuccess) so the queue refreshes even if the second
        // POST fails — the first MOVE order was queued server-side and should
        // appear in the UI immediately, not 5s later when the next poll fires.
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders'] }),
    })

    const onLogout = async () => {
        await logout()
        void navigate('/login', { replace: true })
    }

    return (
        <main className="dashboard">
            <header>
                <div>
                    <h1>Space Game</h1>
                    <p className="muted">Welcome, {user?.username}</p>
                </div>
                <button type="button" onClick={() => void onLogout()}>
                    Log out
                </button>
            </header>

            <section className="ship-card">
                <h2>{ship?.name ?? 'Loading ship…'}</h2>
                <dl>
                    <dt>Position</dt>
                    <dd>{ship ? `(${ship.x}, ${ship.y})` : '—'}</dd>
                    <dt>World tick</dt>
                    <dd>
                        <TickCounter tick={world?.currentTick} />
                    </dd>
                </dl>
            </section>

            <section className="map-card">
                <WorldMapView
                    planets={planets ?? []}
                    ship={ship ?? null}
                    onPlanetClick={(planet) => travelTo.mutate(planet)}
                />
                {travelTo.error && (
                    <p className="form-error" role="alert">
                        Could not queue travel:{' '}
                        {travelTo.error instanceof ApiError
                            ? travelTo.error.message
                            : 'unknown error'}
                    </p>
                )}
            </section>

            <section className="orders-card">
                <h2>Orders</h2>
                <OrdersPanel />
            </section>
        </main>
    )
}
