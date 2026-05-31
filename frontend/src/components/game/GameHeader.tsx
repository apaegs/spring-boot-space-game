import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { TickCounter } from '../TickCounter'

/**
 * Persistent top bar. Player identity on the left, the world clock in the
 * middle, log-out on the right. Stays visible regardless of which ship is
 * selected or which main view is rendered.
 */
export function GameHeader({ tick }: { tick: number | undefined }) {
    const { user, logout } = useAuth()
    const navigate = useNavigate()

    const onLogout = async () => {
        await logout()
        void navigate('/login', { replace: true })
    }

    return (
        <header className="game-header">
            <div className="game-header__left">
                <span className="game-header__title">Space Game</span>
                <span className="game-header__user">{user?.username}</span>
            </div>
            <div className="game-header__tick">
                tick <TickCounter tick={tick} />
            </div>
            <button type="button" className="game-header__logout" onClick={() => void onLogout()}>
                Log out
            </button>
        </header>
    )
}
