import { useEffect, useState, type ReactNode } from 'react'
import {
    deleteAccount as apiDeleteAccount,
    getMe,
    login as apiLogin,
    logout as apiLogout,
    register as apiRegister,
} from '../api/auth'
import type { LoginRequest, MeResponse, RegisterRequest } from '../types/api'
import { AuthContext } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<MeResponse | null | undefined>(undefined)

    // Bootstrap: on mount, ask the backend whether the existing session cookie
    // (if any) is still valid. Drives the initial "is the user logged in?" state.
    useEffect(() => {
        const abort = new AbortController()
        getMe(abort.signal)
            .then((me) => setUser(me))
            .catch(() => setUser(null)) // network errors → treat as logged-out for routing
        return () => abort.abort()
    }, [])

    const login = async (body: LoginRequest) => {
        await apiLogin(body)
        const me = await getMe()
        setUser(me)
    }

    const register = async (body: RegisterRequest) => {
        await apiRegister(body)
        // register doesn't auto-login server-side, so we still need to log in
        // with the same credentials. Same effect: user lands authenticated.
        await apiLogin({ username: body.username, password: body.password })
        // Mirror the login() flow: round-trip through getMe() rather than
        // trusting the register response. Verifies the session is live and
        // ensures the user state matches what /api/auth/me would return.
        const me = await getMe()
        setUser(me)
    }

    const logout = async () => {
        await apiLogout()
        setUser(null)
    }

    const deleteAccount = async () => {
        await apiDeleteAccount()
        // Backend invalidated the session; mirror that locally so the next
        // route guard sees a logged-out state without round-tripping /me.
        setUser(null)
    }

    return (
        <AuthContext.Provider value={{ user, login, register, logout, deleteAccount }}>
            {children}
        </AuthContext.Provider>
    )
}
