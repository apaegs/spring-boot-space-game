import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'

/**
 * Tests for the thin fetch wrapper. The two contracts under test are:
 *   1. CSRF header is sent on POST/PUT/PATCH/DELETE (read from the
 *      XSRF-TOKEN cookie), and NOT sent on GET.
 *   2. Non-2xx responses surface as ApiError with the server's stable shape
 *      ({status, message, details?, errorId?}) — including the network-down
 *      fallback at status 0.
 *
 * fetch is stubbed via vi.stubGlobal so we never touch the real network.
 */

const fetchMock = vi.fn()

beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

afterEach(() => {
    vi.unstubAllGlobals()
})

function okJson(body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
    })
}

function errJson(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    })
}

function lastInit(): RequestInit {
    return fetchMock.mock.calls[fetchMock.mock.calls.length - 1][1] as RequestInit
}

function headerNames(init: RequestInit): string[] {
    const h = init.headers as Record<string, string> | undefined
    return h ? Object.keys(h) : []
}

describe('api.get', () => {
    it('does not attach X-XSRF-TOKEN', async () => {
        document.cookie = 'XSRF-TOKEN=t0k3n; path=/'
        fetchMock.mockResolvedValueOnce(okJson({ ok: true }))

        await api.get('/api/world')

        expect(headerNames(lastInit())).not.toContain('X-XSRF-TOKEN')
    })

    it('rides session cookies via credentials: include', async () => {
        fetchMock.mockResolvedValueOnce(okJson({ ok: true }))

        await api.get('/api/world')

        expect(lastInit().credentials).toBe('include')
    })

    it('decodes URL-encoded CSRF tokens before echoing them', async () => {
        // Spring writes the cookie URL-encoded if the token contains special
        // chars; the header must carry the decoded original.
        document.cookie = `XSRF-TOKEN=${encodeURIComponent('a/b+c=')}; path=/`
        fetchMock.mockResolvedValueOnce(okJson({ ok: true }))

        await api.post('/api/auth/logout')

        const headers = lastInit().headers as Record<string, string>
        expect(headers['X-XSRF-TOKEN']).toBe('a/b+c=')
    })
})

describe('api state-changing methods', () => {
    it.each(['post', 'patch', 'delete'] as const)(
        'attaches X-XSRF-TOKEN from the cookie on %s',
        async (method) => {
            document.cookie = 'XSRF-TOKEN=t0k3n; path=/'
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

            await api[method]('/api/whatever', method === 'delete' ? undefined : {})

            const headers = lastInit().headers as Record<string, string>
            expect(headers['X-XSRF-TOKEN']).toBe('t0k3n')
        }
    )

    it('omits the header when the cookie is absent', async () => {
        // No cookie set → no header. The server may still 403; that's fine,
        // the client's job is to forward what it has, not invent a token.
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

        await api.post('/api/auth/login', { username: 'a', password: 'b' })

        expect(headerNames(lastInit())).not.toContain('X-XSRF-TOKEN')
    })

    it('sets Content-Type: application/json when a body is provided', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

        await api.post('/api/ships', { name: 'Voyager' })

        const headers = lastInit().headers as Record<string, string>
        expect(headers['Content-Type']).toBe('application/json')
        expect(lastInit().body).toBe(JSON.stringify({ name: 'Voyager' }))
    })

    it('returns undefined on 204 No Content', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

        const result = await api.post('/api/auth/logout')

        expect(result).toBeUndefined()
    })
})

describe('ApiError', () => {
    it('carries the full ApiErrorResponse shape on validation 400s', async () => {
        fetchMock.mockResolvedValueOnce(
            errJson(400, {
                status: 400,
                message: 'Invalid input',
                details: { username: 'must not be blank' },
            })
        )

        await expect(api.post('/api/auth/register', {})).rejects.toMatchObject({
            name: 'ApiError',
            status: 400,
            message: 'Invalid input',
            details: { username: 'must not be blank' },
            errorId: null,
        })
    })

    it('carries errorId on 500 catch-all responses', async () => {
        fetchMock.mockResolvedValueOnce(
            errJson(500, {
                status: 500,
                message: 'Something exploded',
                errorId: '11111111-2222-3333-4444-555555555555',
            })
        )

        await expect(api.get('/api/world')).rejects.toMatchObject({
            name: 'ApiError',
            status: 500,
            errorId: '11111111-2222-3333-4444-555555555555',
        })
    })

    it('falls back to statusText when the error body is not JSON', async () => {
        fetchMock.mockResolvedValueOnce(
            new Response('<html>bad gateway</html>', {
                status: 502,
                statusText: 'Bad Gateway',
            })
        )

        await expect(api.get('/api/world')).rejects.toMatchObject({
            name: 'ApiError',
            status: 502,
            message: 'Bad Gateway',
            details: null,
        })
    })

    it('surfaces network failures as ApiError with status 0', async () => {
        // fetch() itself rejecting = backend down / DNS fail / offline. We use
        // status 0 so the UI can distinguish "no HTTP response at all" from a
        // real HTTP error.
        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

        await expect(api.get('/api/world')).rejects.toMatchObject({
            name: 'ApiError',
            status: 0,
        })
    })
})
