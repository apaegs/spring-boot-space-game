import { api } from './client'
import type { ShipDto } from '../types/api'

export function getMyShip(signal?: AbortSignal): Promise<ShipDto> {
    return api.get<ShipDto>('/api/ship', signal)
}
