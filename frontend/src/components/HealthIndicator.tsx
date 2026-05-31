import { useEffect, useState } from 'react'
import { checkHealth } from '../api/health'

const HEALTH_POLL_INTERVAL_MS = 5000

/**
 * Small banner that surfaces "backend offline" without taking over the screen.
 * Shows nothing when the backend is healthy or while the first probe is in
 * flight — silence on the happy path.
 */
export function HealthIndicator() {
    const [online, setOnline] = useState<boolean | null>(null)

    useEffect(() => {
        let cancelled = false

        const probe = async () => {
            const ok = await checkHealth()
            if (!cancelled) setOnline(ok)
        }

        void probe()
        const interval = setInterval(probe, HEALTH_POLL_INTERVAL_MS)
        return () => {
            cancelled = true
            clearInterval(interval)
        }
    }, [])

    if (online !== false) return null

    return (
        <div role="alert" className="health-indicator">
            ⚠️ Backend offline — retrying…
        </div>
    )
}
