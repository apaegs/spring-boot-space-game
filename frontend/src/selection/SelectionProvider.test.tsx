import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ShipDto } from '../types/api'
import { SelectionProvider } from './SelectionProvider'
import { useSelection } from './SelectionContext'

// SelectionProvider calls listMyShips(); stub it so we control which ships
// the test sees without spinning up a fetch mock per case.
vi.mock('../api/ship', () => ({
    listMyShips: vi.fn(),
}))
import { listMyShips } from '../api/ship'

const listMyShipsMock = vi.mocked(listMyShips)

function ship(id: string, name = id): ShipDto {
    return {
        id,
        name,
        x: 0,
        y: 0,
        shipTypeId: '00000000-0000-0000-0000-000000000001',
        shipTypeName: 'Mothership',
        cargoCapacity: 500,
        extractRate: 10,
        cargo: [],
        createdAt: '2026-01-01T00:00:00Z',
        status: 'IDLE',
    }
}

function wrapper({ children }: { children: ReactNode }) {
    const qc = new QueryClient({
        defaultOptions: { queries: { retry: false, gcTime: 0 } },
    })
    return (
        <QueryClientProvider client={qc}>
            <SelectionProvider>{children}</SelectionProvider>
        </QueryClientProvider>
    )
}

function Probe() {
    const { selection, selectedShipId, setSelection } = useSelection()
    return (
        <div>
            <span data-testid="selection-kind">{selection?.kind ?? 'none'}</span>
            <span data-testid="selection-id">{selection?.kind === 'ship' ? selection.id : ''}</span>
            <span data-testid="selected-ship-id">{selectedShipId ?? ''}</span>
            <button onClick={() => setSelection({ kind: 'body', id: 'b-1' })}>pick-body</button>
            <button onClick={() => setSelection(null)}>pick-none</button>
        </div>
    )
}

beforeEach(() => {
    listMyShipsMock.mockReset()
})

afterEach(() => {
    vi.useRealTimers()
})

describe('SelectionProvider', () => {
    it('defaults to the first ship in the fleet until the user picks', async () => {
        // ships[0] is the oldest (the backend orders by createdAt asc) — that's
        // the auto-created mothership for fresh users.
        listMyShipsMock.mockResolvedValueOnce([ship('s-1'), ship('s-2')])

        render(<Probe />, { wrapper })

        // Resolve the deferred microtasks that useQuery uses to settle.
        await vi.waitFor(() => {
            expect(screen.getByTestId('selection-id')).toHaveTextContent(/^s-1$/)
        })
        expect(screen.getByTestId('selection-kind')).toHaveTextContent('ship')
        expect(screen.getByTestId('selected-ship-id')).toHaveTextContent('s-1')
    })

    it('starts at "none" while ships are still loading', () => {
        // never-resolving promise = perpetually loading; no auto-selection yet.
        listMyShipsMock.mockReturnValueOnce(new Promise<ShipDto[]>(() => {}))

        render(<Probe />, { wrapper })

        expect(screen.getByTestId('selection-kind')).toHaveTextContent('none')
        expect(screen.getByTestId('selected-ship-id')).toHaveTextContent('')
    })

    it('respects an explicit deselect even when ships exist', async () => {
        listMyShipsMock.mockResolvedValueOnce([ship('s-1')])

        render(<Probe />, { wrapper })
        await vi.waitFor(() => {
            expect(screen.getByTestId('selection-id')).toHaveTextContent(/^s-1$/)
        })

        const user = userEvent.setup()
        await user.click(screen.getByText('pick-none'))

        // User explicitly chose null. We must NOT snap back to the first ship.
        expect(screen.getByTestId('selection-kind')).toHaveTextContent('none')
        expect(screen.getByTestId('selected-ship-id')).toHaveTextContent('')
    })

    it('selectedShipId is null when a body is selected', async () => {
        listMyShipsMock.mockResolvedValueOnce([ship('s-1')])

        render(<Probe />, { wrapper })
        await vi.waitFor(() => {
            expect(screen.getByTestId('selection-id')).toHaveTextContent(/^s-1$/)
        })

        const user = userEvent.setup()
        await user.click(screen.getByText('pick-body'))

        expect(screen.getByTestId('selection-kind')).toHaveTextContent('body')
        expect(screen.getByTestId('selected-ship-id')).toHaveTextContent('')
    })

    it('useSelection throws when used outside the provider', () => {
        // Suppress the React error-boundary spew that comes with a thrown render.
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

        expect(() =>
            act(() => {
                render(<Probe />)
            })
        ).toThrow(/useSelection must be used inside/)

        consoleError.mockRestore()
    })
})
