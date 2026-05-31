/**
 * GET /api/health — liveness check that the backend is reachable.
 *
 * Returns true if the endpoint returns 200 with `{ "status": "ok" }`, false on
 * any network error, non-2xx, or unexpected body. Never throws — the caller
 * just gets a boolean and decides what to render.
 */
export async function checkHealth(): Promise<boolean> {
    try {
        const res = await fetch('/api/health')
        if (!res.ok) return false
        const body = (await res.json()) as { status?: string }
        return body.status === 'ok'
    } catch {
        return false
    }
}
