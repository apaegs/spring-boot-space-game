import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getMyShip } from '../api/ship'
import { getWorld } from '../api/world'
import { useAuth } from '../auth/AuthContext'
import { OrdersPanel } from '../components/OrdersPanel'

/** Refetch ship + world this often so movement / ticks visibly advance. */
const POLL_MS = 5000

export function Dashboard() {
    const { user, logout } = useAuth()
    const navigate = useNavigate()

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
                    <dd>{world ? world.currentTick : '—'}</dd>
                </dl>
            </section>

            <section className="orders-card">
                <h2>Orders</h2>
                <OrdersPanel />
            </section>

            <p className="muted">Map + click-to-travel coming in the next commits.</p>
        </main>
    )
}
