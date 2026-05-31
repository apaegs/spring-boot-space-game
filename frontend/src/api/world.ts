import { api } from './client'
import type { WorldDto } from '../types/api'

export function getWorld(signal?: AbortSignal): Promise<WorldDto> {
    return api.get<WorldDto>('/api/world', signal)
}
