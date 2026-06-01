import { api } from './client'
import type { CreateOrderRequest, ShipOrderDto } from '../types/api'

/**
 * Orders are ship-scoped on the backend: `/api/ships/{shipId}/orders`. The
 * frontend therefore always knows which ship it's acting on (selection state)
 * before talking to these endpoints.
 */

export function listOrders(shipId: string, signal?: AbortSignal): Promise<ShipOrderDto[]> {
    return api.get<ShipOrderDto[]>(`/api/ships/${shipId}/orders`, signal)
}

export function createOrder(shipId: string, body: CreateOrderRequest): Promise<ShipOrderDto> {
    return api.post<ShipOrderDto>(`/api/ships/${shipId}/orders`, body)
}

export function cancelOrder(shipId: string, orderId: string): Promise<void> {
    return api.delete<void>(`/api/ships/${shipId}/orders/${orderId}`)
}
