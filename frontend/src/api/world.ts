import { api } from './client'
import type { PublicShipDto, WorldDto } from '../types/api'

export function getWorld(signal?: AbortSignal): Promise<WorldDto> {
    return api.get<WorldDto>('/api/world', signal)
}

/**
 * Every ship in the world — caller's own + every other player's. Backend
 * returns the narrower {@link PublicShipDto} (no owner, no audit fields).
 * The own ships are duplicated against {@code listMyShips}; the frontend
 * dedupes by id when merging.
 */
export function listWorldShips(signal?: AbortSignal): Promise<PublicShipDto[]> {
    return api.get<PublicShipDto[]>('/api/world/ships', signal)
}
