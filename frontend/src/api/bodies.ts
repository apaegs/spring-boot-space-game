import { api } from './client'
import type { CelestialBodyDto } from '../types/api'

/**
 * Read-only catalog of celestial bodies (planets, asteroids, gas giants, stars).
 * Replaces the v1 {@code listPlanets} endpoint — see DOMAIN.md "CelestialBody".
 */
export function listBodies(signal?: AbortSignal): Promise<CelestialBodyDto[]> {
    return api.get<CelestialBodyDto[]>('/api/bodies', signal)
}
