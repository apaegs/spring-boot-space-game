import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/client'

/**
 * Confirm-destructive-action dialog for "Delete account". The double-confirm
 * is the username-type-back gate: the player must literally type their own
 * username before the Delete button enables. Mistaken clicks on the
 * affordance in the header don't end an account.
 *
 * <p>Rendered as a {@code <dialog>} so the browser handles the focus trap,
 * ESC-to-close, and backdrop dismissal for us — no third-party modal lib.
 */
export function DeleteAccountDialog({
    open,
    username,
    onCancel,
    onConfirm,
}: {
    open: boolean
    username: string
    onCancel: () => void
    onConfirm: () => Promise<void>
}) {
    const dialogRef = useRef<HTMLDialogElement>(null)
    const inputRef = useRef<HTMLInputElement>(null)
    const [typed, setTyped] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        const dialog = dialogRef.current
        if (!dialog) return
        if (open && !dialog.open) {
            dialog.showModal()
            // Reset state every time the dialog re-opens so the typed value
            // and error from a previous attempt don't leak.
            setTyped('')
            setError(null)
            setTimeout(() => inputRef.current?.focus(), 0)
        } else if (!open && dialog.open) {
            dialog.close()
        }
    }, [open])

    // username.length > 0 closes a defense-in-depth gap: if a caller ever
    // mounted the dialog with an empty string (e.g. during the brief gap
    // between session expiry and the auth redirect) the empty input would
    // match `typed === username` and bypass the type-back confirmation.
    const canDelete = username.length > 0 && typed === username && !submitting

    const submit = async () => {
        if (!canDelete) return
        setSubmitting(true)
        setError(null)
        try {
            await onConfirm()
            // Parent handles redirect; nothing more to do here.
        } catch (e) {
            setSubmitting(false)
            setError(
                e instanceof ApiError && e.message ? e.message : 'Could not delete account.'
            )
        }
    }

    return (
        <dialog
            ref={dialogRef}
            className="confirm-dialog"
            onCancel={(e) => {
                // Browser fires `cancel` on ESC / backdrop. Block when a delete
                // is in flight — we don't want the panel to close on top of an
                // in-progress mutation.
                if (submitting) {
                    e.preventDefault()
                    return
                }
                onCancel()
            }}
        >
            <h2>Delete account?</h2>
            <p>
                This will permanently remove your account, your ships, and any queued orders.
                There is no undo.
            </p>
            <p>
                To confirm, type your username (<strong>{username}</strong>) below.
            </p>
            <input
                ref={inputRef}
                type="text"
                className="confirm-dialog__input"
                value={typed}
                onChange={(e) => setTyped(e.target.value)}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' && canDelete) void submit()
                }}
                autoComplete="off"
                aria-label="Type your username to confirm"
            />
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            <div className="confirm-dialog__actions">
                <button type="button" onClick={onCancel} disabled={submitting}>
                    Cancel
                </button>
                <button
                    type="button"
                    className="confirm-dialog__danger"
                    onClick={() => void submit()}
                    disabled={!canDelete}
                >
                    {submitting ? 'Deleting…' : 'Delete account'}
                </button>
            </div>
        </dialog>
    )
}
