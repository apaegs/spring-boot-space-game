import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function RegisterPage() {
    const { user, register } = useAuth()
    const navigate = useNavigate()

    const [username, setUsername] = useState('')
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    if (user) return <Navigate to="/" replace />

    const onSubmit = async (e: FormEvent) => {
        e.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await register({ username, email, password })
            void navigate('/', { replace: true })
        } catch (err) {
            setError(err instanceof ApiError ? err.message : 'Registration failed')
            setSubmitting(false)
        }
    }

    return (
        <main className="auth-page">
            <h1>Create an account</h1>
            <form onSubmit={onSubmit}>
                <label>
                    Username
                    <input
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        autoComplete="username"
                        required
                        minLength={3}
                        maxLength={32}
                    />
                </label>
                <label>
                    Email
                    <input
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        autoComplete="email"
                        required
                    />
                </label>
                <label>
                    Password
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        autoComplete="new-password"
                        required
                        minLength={8}
                    />
                </label>
                {error && <p className="form-error">{error}</p>}
                <button type="submit" disabled={submitting}>
                    {submitting ? 'Creating…' : 'Create account'}
                </button>
            </form>
            <p>
                Already have an account? <Link to="/login">Log in</Link>
            </p>
        </main>
    )
}
