/**
 * Thin wrapper around fetch that:
 *   - sends/receives JSON
 *   - rides session cookies (`credentials: 'include'` — required so
 *     JSESSIONID set by the backend gets stored and replayed)
 *   - reads the CSRF token from the {@code XSRF-TOKEN} cookie and echoes
 *     it as the {@code X-XSRF-TOKEN} header on every state-changing
 *     request (POST/PUT/PATCH/DELETE)
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

/**
 * Methods that the server's CSRF filter expects a token on. Safe methods
 * (GET/HEAD/OPTIONS/TRACE) are skipped — sending a token on them costs
 * nothing but it's not part of the contract.
 */
const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

/**
 * Read the {@code XSRF-TOKEN} cookie value. Returns null when no cookie is
 * set yet (very first page load before any backend response has landed) —
 * callers tolerate the missing header on register/login (those endpoints are
 * CSRF-exempt server-side) and never reach a state-changing call before some
 * GET has populated the cookie elsewhere.
 */
function readCsrfToken(): string | null {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
    return match ? decodeURIComponent(match[1]) : null
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
    const headers: Record<string, string> = {}
    if (options.body !== undefined) headers['Content-Type'] = 'application/json'
    if (STATE_CHANGING_METHODS.has(method)) {
        const csrf = readCsrfToken()
        if (csrf) headers['X-XSRF-TOKEN'] = csrf
    }

    const init: RequestInit = {
        method,
        credentials: 'include',
        signal: options.signal,
        headers: Object.keys(headers).length > 0 ? headers : undefined,
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
