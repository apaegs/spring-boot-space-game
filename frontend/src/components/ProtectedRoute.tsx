import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Route guard for pages that require an authenticated user.
 *
 * Three states from the AuthContext:
 *   - undefined: initial bootstrap still in flight → show a thin loading
 *     placeholder rather than flash-redirecting to /login.
 *   - null: definitively logged out → redirect to /login.
 *   - MeResponse: render the protected route.
 */
export function ProtectedRoute() {
    const { user } = useAuth()

    if (user === undefined) {
        return <p className="loading">Loading…</p>
    }
    if (user === null) {
        return <Navigate to="/login" replace />
    }
    return <Outlet />
}
