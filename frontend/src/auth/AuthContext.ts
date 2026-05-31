import { createContext, useContext } from 'react'
import type { LoginRequest, MeResponse, RegisterRequest } from '../types/api'

export type AuthState = {
    /** null = not logged in, MeResponse = logged in. `undefined` is the initial-load state. */
    user: MeResponse | null | undefined
    login: (body: LoginRequest) => Promise<void>
    register: (body: RegisterRequest) => Promise<void>
    logout: () => Promise<void>
}

// Lives in a .ts (no JSX) file so Fast Refresh doesn't complain about mixing
// component and non-component exports in the same file.
export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
    const ctx = useContext(AuthContext)
    if (!ctx) {
        throw new Error('useAuth must be used inside <AuthProvider>')
    }
    return ctx
}
