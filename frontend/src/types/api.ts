/**
 * Hand-written TypeScript shapes that mirror the backend DTOs.
 *
 * Kept manual (not generated from OpenAPI) for v1 simplicity — the API surface
 * is small enough that the cognitive overhead of a generator isn't worth it
 * yet. When endpoints multiply, switch to openapi-typescript or similar.
 *
 * Keep these in sync with the matching `*Dto` record in the backend whenever
 * a DTO changes. CLAUDE.md "Entity vs DTO" rule means DTOs are the contract.
 */

// --- auth ---

export type MeResponse = {
    id: string
    username: string
    email: string
    createdAt: string
}

export type RegisterRequest = {
    username: string
    email: string
    password: string
}

export type LoginRequest = {
    username: string
    password: string
}

// --- ship ---

export type ShipStatus = 'IDLE' | 'MOVING' | 'LANDED'

export type ShipDto = {
    id: string
    name: string
    x: number
    y: number
    createdAt: string
    status: ShipStatus
}

/**
 * Body for `POST /api/ships`. {@code name} is optional — empty body or
 * omitted {@code name} yields the backend-generated auto-name.
 */
export type CreateShipRequest = {
    name?: string
}

/**
 * Public projection of any ship in the world (own or foreign). Matches the
 * backend's {@code PublicShipDto} — deliberately narrower than {@link ShipDto}:
 * no {@code createdAt}, no owner info. Used to render foreign ships on the
 * map without leaking who owns them.
 */
export type PublicShipDto = {
    id: string
    name: string
    x: number
    y: number
}

// --- world ---

export type WorldDto = {
    currentTick: number
    lastTickAt: string
}

// --- planets ---

export type PlanetDto = {
    id: string
    x: number
    y: number
    name: string
    description: string | null
}

// --- orders ---

export type OrderKind = 'MOVE' | 'LAND'
export type OrderStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export type ShipOrderDto = {
    id: string
    shipId: string
    kind: OrderKind
    params: Record<string, unknown>
    status: OrderStatus
    createdAt: string
    startedAt: string | null
    completedAt: string | null
}

export type CreateOrderRequest = {
    kind: OrderKind
    params?: Record<string, unknown>
}
