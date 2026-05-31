import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Stub dashboard — the real ship info + orders panel + map land in later
 * commits in this PR. For now: greet the user and let them log out so the
 * auth flow has a complete loop.
 */
export function Dashboard() {
    const { user, logout } = useAuth()
    const navigate = useNavigate()

    const onLogout = async () => {
        await logout()
        void navigate('/login', { replace: true })
    }

    return (
        <main className="dashboard">
            <header>
                <h1>Space Game</h1>
                <button type="button" onClick={() => void onLogout()}>
                    Log out
                </button>
            </header>
            <p>Welcome, {user?.username}. Ship dashboard + map coming in the next commits.</p>
        </main>
    )
}
