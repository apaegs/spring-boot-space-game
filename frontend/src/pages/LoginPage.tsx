import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
    const { user, login } = useAuth()
    const navigate = useNavigate()

    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    // Already logged in? Skip the form and go to the dashboard.
    if (user) return <Navigate to="/" replace />

    const onSubmit = async (e: FormEvent) => {
        e.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await login({ username, password })
            void navigate('/', { replace: true })
        } catch (err) {
            setError(err instanceof ApiError ? err.message : 'Login failed')
            setSubmitting(false)
        }
    }

    return (
        <main className="auth-page">
            <h1>Log in</h1>
            <form onSubmit={onSubmit}>
                <label>
                    Username
                    <input
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        autoComplete="username"
                        required
                    />
                </label>
                <label>
                    Password
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        autoComplete="current-password"
                        required
                    />
                </label>
                {error && <p className="form-error">{error}</p>}
                <button type="submit" disabled={submitting}>
                    {submitting ? 'Logging in…' : 'Log in'}
                </button>
            </form>
            <p>
                New here? <Link to="/register">Create an account</Link>
            </p>
        </main>
    )
}
