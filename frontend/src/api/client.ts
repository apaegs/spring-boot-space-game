/**
 * Thin wrapper around fetch that:
 *   - sends/receives JSON
 *   - rides session cookies (`credentials: 'include'` — required so
 *     JSESSIONID set by the backend gets stored and replayed)
 *   - turns non-2xx responses into typed `ApiError`s the UI can branch on
 *
 * Each endpoint-specific module (auth, ship, world, planets, orders) builds
 * on this so error handling is uniform across the SPA.
 */

export class ApiError extends Error {
    readonly status: number

    constructor(status: number, message: string) {
        super(message)
        this.name = 'ApiError'
        this.status = status
    }
}

type RequestOptions = {
    body?: unknown
    signal?: AbortSignal
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
    const init: RequestInit = {
        method,
        credentials: 'include',
        signal: options.signal,
        headers: options.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    }

    let res: Response
    try {
        res = await fetch(path, init)
    } catch {
        // Network-level failure (backend down, DNS error, etc.). Surface as
        // status 0 so the UI can distinguish from a real HTTP error.
        throw new ApiError(0, 'Network error — is the backend running?')
    }

    if (res.status === 204) {
        return undefined as T
    }

    if (!res.ok) {
        // Try to extract a server-supplied message; fall back to status text.
        let message = res.statusText
        try {
            const errBody = (await res.json()) as { message?: string; error?: string }
            message = errBody.message ?? errBody.error ?? message
        } catch {
            // Body wasn't JSON; use status text as-is.
        }
        throw new ApiError(res.status, message)
    }

    return (await res.json()) as T
}

export const api = {
    get: <T>(path: string, signal?: AbortSignal) => request<T>('GET', path, { signal }),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
    patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
    delete: <T>(path: string) => request<T>('DELETE', path),
}
