import { api, ApiError } from './client'
import type { LoginRequest, MeResponse, RegisterRequest } from '../types/api'

export async function getMe(signal?: AbortSignal): Promise<MeResponse | null> {
    try {
        return await api.get<MeResponse>('/api/auth/me', signal)
    } catch (e) {
        // 401 is the "not logged in" signal — translate to null so the
        // AuthContext can treat it as expected state, not an error.
        if (e instanceof ApiError && e.status === 401) return null
        throw e
    }
}

export function login(body: LoginRequest): Promise<void> {
    return api.post<void>('/api/auth/login', body)
}

export function register(body: RegisterRequest): Promise<MeResponse> {
    return api.post<MeResponse>('/api/auth/register', body)
}

export function logout(): Promise<void> {
    return api.post<void>('/api/auth/logout')
}
