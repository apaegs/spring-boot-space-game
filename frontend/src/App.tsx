import { useEffect, useState } from 'react'
import { checkHealth } from './api/health'
import './App.css'

const HEALTH_POLL_INTERVAL_MS = 5000

function App() {
    // null = check in progress; boolean = last known state. We poll rather than
    // fetch-once so stopping the backend visibly updates the UI within a few
    // seconds — that's part of the acceptance criteria for #6.
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

    return (
        <main className="app">
            <h1>Space Game</h1>
            <p className="status">
                Backend: {online === null && <span>checking…</span>}
                {online === true && <span className="online">✅ online</span>}
                {online === false && <span className="offline">❌ unreachable</span>}
            </p>
        </main>
    )
}

export default App
