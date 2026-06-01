import { api } from './client'
import type { CreateShipRequest, ShipDto } from '../types/api'

export function listMyShips(signal?: AbortSignal): Promise<ShipDto[]> {
    return api.get<ShipDto[]>('/api/ships', signal)
}

export function createShip(body: CreateShipRequest = {}): Promise<ShipDto> {
    return api.post<ShipDto>('/api/ships', body)
}
