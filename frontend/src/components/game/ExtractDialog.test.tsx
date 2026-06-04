import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CelestialBodyDto } from '../../types/api'
import { ExtractDialog } from './ExtractDialog'

// jsdom doesn't implement HTMLDialogElement.showModal/close natively, so stub
// them here to keep the dialog rendering machinery alive during tests.
HTMLDialogElement.prototype.showModal = function () {
    this.open = true
}
HTMLDialogElement.prototype.close = function () {
    this.open = false
}

function body(overrides: Partial<CelestialBodyDto> = {}): CelestialBodyDto {
    return {
        id: 'body-1',
        x: 50,
        y: 50,
        name: 'Earth',
        description: null,
        kind: 'ROCKY_PLANET',
        reserves: [
            { kind: 'IRON', reserve: 1200 },
            { kind: 'WATER', reserve: 0 }, // depleted — must be filtered out of the picker
        ],
        buyPrices: [],
        ...overrides,
    }
}

describe('ExtractDialog', () => {
    it('lists only resources with reserve > 0', () => {
        render(
            <ExtractDialog
                open={true}
                body={body()}
                onCancel={() => {}}
                onConfirm={async () => {}}
            />
        )

        expect(screen.getByText('IRON')).toBeInTheDocument()
        expect(screen.queryByText('WATER')).not.toBeInTheDocument()
        // Reserve label uses thousands separators ("1,200 left").
        expect(screen.getByText(/1,200 left/)).toBeInTheDocument()
    })

    it('renders the empty-body message when nothing is extractable', () => {
        const allDepleted = body({
            reserves: [{ kind: 'IRON', reserve: 0 }],
        })

        render(
            <ExtractDialog
                open={true}
                body={allDepleted}
                onCancel={() => {}}
                onConfirm={async () => {}}
            />
        )

        expect(screen.getByText(/has no extractable resources/i)).toBeInTheDocument()
        // The queue button is disabled when nothing's available.
        expect(screen.getByRole('button', { name: /queue extract/i })).toBeDisabled()
    })

    it('queues an EXTRACT with the chosen resource and default until_cancelled mode', async () => {
        const onConfirm = vi.fn().mockResolvedValue(undefined)

        render(
            <ExtractDialog
                open={true}
                body={body()}
                onCancel={() => {}}
                onConfirm={onConfirm}
            />
        )

        // Default selection should be the first available resource (IRON).
        const queueBtn = screen.getByRole('button', { name: /queue extract/i })
        const { default: userEvent } = await import('@testing-library/user-event')
        const user = userEvent.setup()
        await user.click(queueBtn)

        expect(onConfirm).toHaveBeenCalledWith('IRON', 'until_cancelled')
    })
})
