import { api } from './client'
import type { CreateOrderRequest, ShipOrderDto } from '../types/api'

export function listOrders(signal?: AbortSignal): Promise<ShipOrderDto[]> {
    return api.get<ShipOrderDto[]>('/api/ship/orders', signal)
}

export function createOrder(body: CreateOrderRequest): Promise<ShipOrderDto> {
    return api.post<ShipOrderDto>('/api/ship/orders', body)
}

export function cancelOrder(id: string): Promise<void> {
    return api.delete<void>(`/api/ship/orders/${id}`)
}
