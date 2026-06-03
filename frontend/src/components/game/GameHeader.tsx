import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { TickCounter } from '../TickCounter'
import { DeleteAccountDialog } from './DeleteAccountDialog'

/**
 * Persistent top bar. Player identity on the left, the world clock in the
 * middle, log-out and delete-account on the right. Stays visible regardless
 * of which ship is selected or which main view is rendered.
 */
export function GameHeader({ tick }: { tick: number | undefined }) {
    const { user, logout, deleteAccount } = useAuth()
    const navigate = useNavigate()
    const [deleteOpen, setDeleteOpen] = useState(false)

    const onLogout = async () => {
        await logout()
        void navigate('/login', { replace: true })
    }

    const onConfirmDelete = async () => {
        await deleteAccount()
        // Backend invalidated the session; route to /login. `replace: true`
        // so the back button doesn't return into a now-401 game view.
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
            <div className="game-header__actions">
                <button
                    type="button"
                    className="game-header__delete"
                    onClick={() => setDeleteOpen(true)}
                    title="Delete account"
                >
                    Delete account
                </button>
                <button
                    type="button"
                    className="game-header__logout"
                    onClick={() => void onLogout()}
                >
                    Log out
                </button>
            </div>
            <DeleteAccountDialog
                open={deleteOpen}
                username={user?.username ?? ''}
                onCancel={() => setDeleteOpen(false)}
                onConfirm={onConfirmDelete}
            />
        </header>
    )
}
