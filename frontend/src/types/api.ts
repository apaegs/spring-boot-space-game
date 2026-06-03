/**
 * Hand-written TypeScript shapes that mirror the backend DTOs.
 *
 * Kept manual (not generated from OpenAPI) for v1 simplicity — the API surface
 * is small enough that the cognitive overhead of a generator isn't worth it
 * yet. When endpoints multiply, switch to openapi-typescript or similar.
 *
 * Keep these in sync with the matching `*Dto` record in the backend whenever
 * a DTO changes. CLAUDE.md "Entity vs DTO" rule means DTOs are the contract.
 *
 * Backing Java source-of-truth (one per type below). If you change a record
 * over there, the corresponding type here MUST be updated in the same PR —
 * nothing binds them at build time:
 *
 *   MeResponse           → auth/MeResponse.java
 *   RegisterRequest      → auth/RegisterRequest.java
 *   LoginRequest         → auth/LoginRequest.java
 *   ShipStatus           → ship/ShipStatus.java
 *   ShipDto              → ship/ShipDto.java
 *   CreateShipRequest    → ship/CreateShipRequest.java
 *   PublicShipDto        → ship/PublicShipDto.java
 *   ShipType / ShipCargo → ship/ShipType.java, ship/ShipCargo.java
 *   WorldDto             → world/WorldDto.java
 *   CelestialBodyDto     → body/CelestialBodyDto.java
 *   CelestialBodyKind    → body/CelestialBodyKind.java
 *   ResourceKind         → resource/ResourceKind.java
 *   OrderKind            → order/OrderKind.java
 *   OrderStatus          → order/OrderStatus.java
 *   ShipOrderDto         → order/ShipOrderDto.java
 *   CreateOrderRequest   → order/CreateOrderRequest.java
 *
 * Paths are relative to src/main/java/org/example/springbootspacegame/.
 */

// --- auth ---

export type MeResponse = {
    id: string
    username: string
    email: string
    /** In-game currency on the user. Earned by SELL (PR 2). */
    credits: number
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

// --- resources ---

/**
 * Catalog of resources a ship can carry and a body can yield or buy.
 * Mirrors `resource/ResourceKind.java`. New kinds = add a literal here AND
 * a Java enum value in the same PR.
 */
export type ResourceKind = 'IRON' | 'WATER' | 'HYDROGEN' | 'HELIUM' | 'RARE_METAL'

// --- ship ---

export type ShipStatus = 'IDLE' | 'MOVING' | 'LANDED' | 'ORBITING'

export type ShipDto = {
    id: string
    name: string
    x: number
    y: number
    /** FK into the ship types catalog. Stats (cargo cap, extract rate) live on the type, not the ship. */
    shipTypeId: string
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

/**
 * Catalog entry for a ship type — cargo capacity, extraction rate, etc. v1 has
 * exactly one row ({@code MOTHERSHIP}); future types are new rows.
 */
export type ShipTypeDto = {
    id: string
    code: string
    name: string
    cargoCapacity: number
    extractRate: number
}

/** One row of a ship's cargo hold ({@code (shipId, resourceKind, qty)} composite key). */
export type ShipCargoDto = {
    resourceKind: ResourceKind
    qty: number
}

// --- world ---

export type WorldDto = {
    currentTick: number
    lastTickAt: string
}

// --- celestial bodies ---

/**
 * Body taxonomy. {@code STAR} is decorative — no extraction, no LAND target.
 * {@code GAS_GIANT} ships will end up in {@code ORBITING} rather than
 * {@code LANDED} after the PR 2 LAND handler update.
 */
export type CelestialBodyKind =
    | 'ROCKY_PLANET'
    | 'LAVA_PLANET'
    | 'ICE_PLANET'
    | 'GAS_GIANT'
    | 'ASTEROID'
    | 'STAR'

/** Per-resource reserve on a body. */
export type BodyResourceReserve = {
    kind: ResourceKind
    reserve: number
}

/** Per-resource buy price on a body (only present for resources the body buys). */
export type BodyResourceBuyPrice = {
    kind: ResourceKind
    pricePerUnit: number
}

export type CelestialBodyDto = {
    id: string
    x: number
    y: number
    name: string
    description: string | null
    kind: CelestialBodyKind
    reserves: BodyResourceReserve[]
    buyPrices: BodyResourceBuyPrice[]
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
