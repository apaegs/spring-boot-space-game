import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../api/client'
import type { CelestialBodyDto, ResourceKind, ShipCargoEntry } from '../../types/api'

/**
 * Modal to queue a SELL order. Only resources the ship actually has AND the
 * body actually buys are listed — the intersection is the meaningful set.
 */
export function SellDialog({
    open,
    body,
    cargo,
    onCancel,
    onConfirm,
}: {
    open: boolean
    body: CelestialBodyDto
    cargo: ShipCargoEntry[]
    onCancel: () => void
    onConfirm: (resourceKind: ResourceKind) => Promise<void>
}) {
    const dialogRef = useRef<HTMLDialogElement>(null)
    const [resource, setResource] = useState<ResourceKind | null>(null)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    // The intersection of "what I have" and "what they buy", with the body's
    // per-resource price decorated on for display.
    const offers = useMemo(() => {
        const cargoByKind = new Map(cargo.map((c) => [c.resourceKind, c.qty]))
        return body.buyPrices
            .filter((p) => (cargoByKind.get(p.kind) ?? 0) > 0)
            .map((p) => ({
                kind: p.kind,
                pricePerUnit: p.pricePerUnit,
                qty: cargoByKind.get(p.kind) ?? 0,
            }))
    }, [body.buyPrices, cargo])

    useEffect(() => {
        const dialog = dialogRef.current
        if (!dialog) return
        if (open && !dialog.open) {
            dialog.showModal()
            setResource(offers[0]?.kind ?? null)
            setError(null)
            // Mirror ExtractDialog — clear submitting on every fresh open so a
            // previous successful submit doesn't leave the actions stuck.
            setSubmitting(false)
        } else if (!open && dialog.open) {
            dialog.close()
        }
    }, [open, offers])

    // Derived during render rather than via setState-in-effect (the
    // react-hooks/set-state-in-effect rule forbids the effect pattern — it
    // forces cascading renders). If offers change while the dialog is open
    // and the stored choice is no longer sellable, use the first valid
    // option as the effective selection. `setResource` is still the
    // committed value (user clicks update it).
    const effectiveResource: ResourceKind | null =
        resource !== null && offers.some((o) => o.kind === resource)
            ? resource
            : (offers[0]?.kind ?? null)

    const canSell = effectiveResource !== null && !submitting

    const submit = async () => {
        if (!canSell || effectiveResource === null) return
        setSubmitting(true)
        setError(null)
        try {
            await onConfirm(effectiveResource)
        } catch (e) {
            setError(e instanceof ApiError && e.message ? e.message : 'Could not queue sell.')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <dialog
            ref={dialogRef}
            className="sell-dialog"
            onCancel={(e) => {
                if (submitting) {
                    e.preventDefault()
                    return
                }
                onCancel()
            }}
        >
            <h2>Sell at {body.name}</h2>

            {offers.length === 0 ? (
                <p>Nothing in cargo matches what {body.name} buys.</p>
            ) : (
                <>
                    <p>Pick a resource. The ship's entire stock of that resource sells in one tick.</p>
                    <fieldset className="sell-dialog__resources">
                        <legend>Resource</legend>
                        {offers.map((o) => (
                            <label key={o.kind} className="sell-dialog__radio">
                                <input
                                    type="radio"
                                    name="resource"
                                    value={o.kind}
                                    checked={effectiveResource === o.kind}
                                    onChange={() => setResource(o.kind)}
                                />
                                <span className="sell-dialog__resource-name">{o.kind}</span>
                                <span className="sell-dialog__sale">
                                    {o.qty.toLocaleString('en-US')} ×{' '}
                                    {o.pricePerUnit.toLocaleString('en-US')} cr ={' '}
                                    <strong>{(o.qty * o.pricePerUnit).toLocaleString('en-US')} cr</strong>
                                </span>
                            </label>
                        ))}
                    </fieldset>
                </>
            )}

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            <div className="sell-dialog__actions">
                <button type="button" onClick={onCancel} disabled={submitting}>
                    Cancel
                </button>
                <button
                    type="button"
                    onClick={() => void submit()}
                    disabled={!canSell || offers.length === 0}
                    className="sell-dialog__queue"
                >
                    {submitting ? 'Queueing…' : 'Queue sell'}
                </button>
            </div>
        </dialog>
    )
}
