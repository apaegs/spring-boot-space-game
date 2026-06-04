import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CelestialBodyDto, ShipCargoEntry } from '../../types/api'
import { SellDialog } from './SellDialog'

HTMLDialogElement.prototype.showModal = function () {
    this.open = true
}
HTMLDialogElement.prototype.close = function () {
    this.open = false
}

const earth: CelestialBodyDto = {
    id: 'body-1',
    x: 50,
    y: 50,
    name: 'Earth',
    description: null,
    kind: 'ROCKY_PLANET',
    reserves: [],
    buyPrices: [
        { kind: 'IRON', pricePerUnit: 9 },
        { kind: 'WATER', pricePerUnit: 7 },
    ],
}

describe('SellDialog', () => {
    it('lists only resources the ship has AND the body buys', () => {
        const cargo: ShipCargoEntry[] = [
            { resourceKind: 'IRON', qty: 50 },
            { resourceKind: 'HELIUM', qty: 30 }, // Earth doesn't buy HELIUM — must be filtered out
        ]

        render(
            <SellDialog
                open={true}
                body={earth}
                cargo={cargo}
                onCancel={() => {}}
                onConfirm={async () => {}}
            />
        )

        expect(screen.getByText('IRON')).toBeInTheDocument()
        // Sale line is "50 × 9 cr = 450 cr".
        expect(screen.getByText(/450 cr/)).toBeInTheDocument()
        // WATER isn't in cargo; HELIUM is in cargo but Earth doesn't buy it.
        expect(screen.queryByText('WATER')).not.toBeInTheDocument()
        expect(screen.queryByText('HELIUM')).not.toBeInTheDocument()
    })

    it('renders the empty-intersection message when nothing matches', () => {
        const cargo: ShipCargoEntry[] = [{ resourceKind: 'HELIUM', qty: 50 }]

        render(
            <SellDialog
                open={true}
                body={earth}
                cargo={cargo}
                onCancel={() => {}}
                onConfirm={async () => {}}
            />
        )

        expect(screen.getByText(/Nothing in cargo matches/i)).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /queue sell/i })).toBeDisabled()
    })

    it('queues a SELL with the chosen resource', async () => {
        const onConfirm = vi.fn().mockResolvedValue(undefined)
        const cargo: ShipCargoEntry[] = [{ resourceKind: 'IRON', qty: 50 }]

        render(
            <SellDialog
                open={true}
                body={earth}
                cargo={cargo}
                onCancel={() => {}}
                onConfirm={onConfirm}
            />
        )

        const { default: userEvent } = await import('@testing-library/user-event')
        const user = userEvent.setup()
        await user.click(screen.getByRole('button', { name: /queue sell/i }))

        expect(onConfirm).toHaveBeenCalledWith('IRON')
    })
})
