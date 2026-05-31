import { useEffect, useState, type ReactNode } from 'react'
import { getMe, login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/auth'
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
        const me = await apiRegister(body)
        // register doesn't auto-login server-side, so we still need to log in
        // with the same credentials. Same effect: user lands authenticated.
        await apiLogin({ username: body.username, password: body.password })
        setUser(me)
    }

    const logout = async () => {
        await apiLogout()
        setUser(null)
    }

    return (
        <AuthContext.Provider value={{ user, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    )
}
