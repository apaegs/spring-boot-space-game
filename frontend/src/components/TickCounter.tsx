import { useAnimate } from 'framer-motion'
import { useEffect, useRef } from 'react'

/** Animation length, in seconds. Matches what the old CSS transition felt like. */
const FLASH_DURATION_S = 0.5
const FLASH_FROM = 'rgba(102, 221, 255, 0.4)'
const FLASH_TO = 'rgba(102, 221, 255, 0)'

/**
 * Tick number with a brief flash animation whenever the value advances.
 * Lets the player see the world is alive even when nothing else on screen
 * changes (idle ship, empty queue).
 *
 * <p>Animation is driven by framer-motion's {@code useAnimate} per the
 * {@code CLAUDE.md} frontend convention ("animated numbers via a lightweight
 * lib"). The flash is a single keyframe interpolation from a cyan highlight
 * back to transparent — no per-render CSS-class toggling, no transition trick.
 */
export function TickCounter({ tick }: { tick: number | undefined }) {
    const [scope, animate] = useAnimate<HTMLSpanElement>()
    const previousRef = useRef<number | undefined>(undefined)

    useEffect(() => {
        if (tick === undefined) return
        // Compare against the immediate previous tick before updating the ref —
        // otherwise the next render would see a value frozen at the time of
        // the last non-flashing render. Use {@code >}, not {@code !==}, so a
        // backwards jump (dev backend reset re-seeding WorldState) doesn't
        // trigger a misleading "the world advanced" flash.
        const advanced = previousRef.current !== undefined && tick > previousRef.current
        previousRef.current = tick
        if (!advanced || !scope.current) return
        void animate(
            scope.current,
            { backgroundColor: [FLASH_FROM, FLASH_TO] },
            { duration: FLASH_DURATION_S, ease: 'easeOut' }
        )
    }, [tick, animate, scope])

    return (
        <span ref={scope} className="tick-counter">
            {tick ?? '—'}
        </span>
    )
}
