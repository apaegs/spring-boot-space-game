import { QueryClient } from '@tanstack/react-query'

/**
 * Shared QueryClient. Defaults tuned for a game-ish app:
 *   - refetchOnWindowFocus: false — we manage refresh cadence ourselves
 *     (e.g. world poll every 5s in the Dashboard) so the UI doesn't
 *     ricochet when alt-tabbing.
 *   - retry: 1 — one retry covers transient network blips without
 *     hiding real failures behind long retry storms.
 */
export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            refetchOnWindowFocus: false,
            retry: 1,
        },
    },
})
