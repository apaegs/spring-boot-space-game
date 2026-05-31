import { useEffect, useRef, useState } from 'react'

const FLASH_MS = 500

/**
 * Tick number with a brief flash animation whenever the value advances.
 * Lets the player see the world is alive even when nothing else on screen
 * changes (idle ship, empty queue).
 */
export function TickCounter({ tick }: { tick: number | undefined }) {
    const [flash, setFlash] = useState(false)
    const previousRef = useRef<number | undefined>(undefined)

    useEffect(() => {
        if (tick === undefined) return
        // Capture whether this tick differs *before* updating previousRef, so
        // the next render compares against the immediate previous tick (not a
        // stale value frozen at the time of the last non-flashing render).
        const changed = previousRef.current !== undefined && tick !== previousRef.current
        previousRef.current = tick
        if (changed) {
            setFlash(true)
            const timer = setTimeout(() => setFlash(false), FLASH_MS)
            return () => clearTimeout(timer)
        }
    }, [tick])

    return (
        <span className={flash ? 'tick-counter tick-counter--flash' : 'tick-counter'}>
            {tick ?? '—'}
        </span>
    )
}
