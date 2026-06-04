import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/client'
import type { CelestialBodyDto, ExtractMode, ResourceKind } from '../../types/api'

/**
 * Modal to queue an EXTRACT order. The player picks a resource (from the body's
 * non-zero reserves) and a duration mode. The dialog tolerates an empty body
 * (no resources at all) and renders a guidance message instead of an empty
 * picker.
 */
export function ExtractDialog({
    open,
    body,
    onCancel,
    onConfirm,
}: {
    open: boolean
    body: CelestialBodyDto
    onCancel: () => void
    onConfirm: (resourceKind: ResourceKind, mode: ExtractMode) => Promise<void>
}) {
    const dialogRef = useRef<HTMLDialogElement>(null)
    const [resource, setResource] = useState<ResourceKind | null>(null)
    const [modeKind, setModeKind] = useState<'until_cancelled' | 'ticks' | 'until_full'>('until_cancelled')
    const [ticks, setTicks] = useState<number>(50)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        const dialog = dialogRef.current
        if (!dialog) return
        if (open && !dialog.open) {
            dialog.showModal()
            // Default to the first resource the body has, if any.
            const firstAvailable = body.reserves.find((r) => r.reserve > 0)?.kind ?? null
            setResource(firstAvailable)
            setModeKind('until_cancelled')
            setTicks(50)
            setError(null)
            // Reset submitting too — a previous open that resolved successfully
            // would have left this `true` since onConfirm closed the dialog
            // before the success branch could clear it.
            setSubmitting(false)
        } else if (!open && dialog.open) {
            dialog.close()
        }
    }, [open, body.reserves])

    const available = body.reserves.filter((r) => r.reserve > 0)
    // If body.reserves changes while the dialog is open and the currently
    // selected resource drops to zero (or disappears), reset to the first
    // still-available one. Without this, the player could submit a stale
    // resource that the handler would just cancel.
    useEffect(() => {
        if (!open) return
        if (resource !== null && !available.some((r) => r.kind === resource)) {
            setResource(available[0]?.kind ?? null)
        }
    }, [open, available, resource])

    const hasValidSelection = resource !== null && available.some((r) => r.kind === resource)
    const canExtract = hasValidSelection && (modeKind !== 'ticks' || ticks > 0) && !submitting

    const submit = async () => {
        if (!canExtract || resource === null) return
        const mode: ExtractMode =
            modeKind === 'until_cancelled'
                ? 'until_cancelled'
                : modeKind === 'ticks'
                  ? { ticks }
                  : { until_full: true }
        setSubmitting(true)
        setError(null)
        try {
            await onConfirm(resource, mode)
        } catch (e) {
            setError(e instanceof ApiError && e.message ? e.message : 'Could not queue extract.')
        } finally {
            // Always clear, even on success — caller closes the dialog but
            // we may remain mounted (the parent doesn't unmount on close),
            // so the next open would otherwise replay a stuck "Queueing…" state.
            setSubmitting(false)
        }
    }

    return (
        <dialog
            ref={dialogRef}
            className="extract-dialog"
            onCancel={(e) => {
                if (submitting) {
                    e.preventDefault()
                    return
                }
                onCancel()
            }}
        >
            <h2>Extract from {body.name}</h2>

            {available.length === 0 ? (
                <p>{body.name} has no extractable resources.</p>
            ) : (
                <>
                    <p>Choose a resource and how long the ship should keep extracting.</p>

                    <fieldset className="extract-dialog__resources">
                        <legend>Resource</legend>
                        {available.map((r) => (
                            <label key={r.kind} className="extract-dialog__radio">
                                <input
                                    type="radio"
                                    name="resource"
                                    value={r.kind}
                                    checked={resource === r.kind}
                                    onChange={() => setResource(r.kind)}
                                />
                                <span className="extract-dialog__resource-name">{r.kind}</span>
                                <span className="extract-dialog__reserve">{r.reserve.toLocaleString('en-US')} left</span>
                            </label>
                        ))}
                    </fieldset>

                    <fieldset className="extract-dialog__modes">
                        <legend>Duration</legend>
                        <label className="extract-dialog__radio">
                            <input
                                type="radio"
                                name="mode"
                                value="until_cancelled"
                                checked={modeKind === 'until_cancelled'}
                                onChange={() => setModeKind('until_cancelled')}
                            />
                            <span>Until cancelled — pauses when cargo is full</span>
                        </label>
                        <label className="extract-dialog__radio">
                            <input
                                type="radio"
                                name="mode"
                                value="ticks"
                                checked={modeKind === 'ticks'}
                                onChange={() => setModeKind('ticks')}
                            />
                            <span>
                                For{' '}
                                <input
                                    type="number"
                                    min={1}
                                    value={ticks}
                                    onChange={(e) => setTicks(Math.max(1, Number(e.target.value)))}
                                    onClick={() => setModeKind('ticks')}
                                    className="extract-dialog__ticks-input"
                                    aria-label="ticks"
                                />{' '}
                                ticks
                            </span>
                        </label>
                        <label className="extract-dialog__radio">
                            <input
                                type="radio"
                                name="mode"
                                value="until_full"
                                checked={modeKind === 'until_full'}
                                onChange={() => setModeKind('until_full')}
                            />
                            <span>Until cargo is full</span>
                        </label>
                    </fieldset>
                </>
            )}

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            <div className="extract-dialog__actions">
                <button type="button" onClick={onCancel} disabled={submitting}>
                    Cancel
                </button>
                <button
                    type="button"
                    onClick={() => void submit()}
                    disabled={!canExtract || available.length === 0}
                    className="extract-dialog__queue"
                >
                    {submitting ? 'Queueing…' : 'Queue extract'}
                </button>
            </div>
        </dialog>
    )
}
