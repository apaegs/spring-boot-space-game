import { api } from './client'
import type { PlanetDto } from '../types/api'

export function listPlanets(signal?: AbortSignal): Promise<PlanetDto[]> {
    return api.get<PlanetDto[]>('/api/planets', signal)
}
