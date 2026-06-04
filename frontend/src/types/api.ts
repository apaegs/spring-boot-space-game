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
 *   ShipCargoEntry       → ship/ShipDto.java (nested CargoEntry record)
 *   CreateShipRequest    → ship/CreateShipRequest.java
 *   PublicShipDto        → ship/PublicShipDto.java
 *   WorldDto             → world/WorldDto.java
 *   CelestialBodyDto     → body/CelestialBodyDto.java
 *   CelestialBodyKind    → body/CelestialBodyKind.java
 *   ResourceKind         → resource/ResourceKind.java
 *   OrderKind            → order/OrderKind.java
 *   OrderStatus          → order/OrderStatus.java
 *   ShipOrderDto         → order/ShipOrderDto.java
 *   CreateOrderRequest   → order/CreateOrderRequest.java
 *   ExtractMode          → order/handlers/ExtractOrderHandler.java (parseMode contract; no Java DTO)
 *   ExtractParams        → order/handlers/ExtractOrderHandler.java (params shape; no Java DTO)
 *   SellParams           → order/handlers/SellOrderHandler.java   (params shape; no Java DTO)
 *
 * Paths are relative to src/main/java/org/example/springbootspacegame/.
 *
 * The {@code Extract*} / {@code SellParams} types document the JSONB params
 * shape — they have no Java DTO counterpart; the contract is enforced at
 * parse time inside the matching handler. Update both sides together when
 * the shape changes.
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

export type ShipStatus = 'IDLE' | 'MOVING' | 'ORBITING'

export type ShipDto = {
    id: string
    name: string
    x: number
    y: number
    /** FK into the ship types catalog. */
    shipTypeId: string
    /** Display name of the ship type (e.g. "Mothership"). Joined in server-side. */
    shipTypeName: string
    /** Total cargo cap across all resources combined. */
    cargoCapacity: number
    /** Units the EXTRACT handler pulls per tick. UI uses it for ETA estimates. */
    extractRate: number
    /** Per-resource cargo rows. Sum of {@code qty} is enforced against {@code cargoCapacity}. */
    cargo: ShipCargoEntry[]
    createdAt: string
    status: ShipStatus
}

/** One row of a ship's cargo hold. Embedded in {@link ShipDto}. */
export type ShipCargoEntry = {
    resourceKind: ResourceKind
    qty: number
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

// --- celestial bodies ---

/** Body taxonomy. {@code STAR} is decorative — no extraction, just a landmark. */
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

export type OrderKind = 'MOVE' | 'EXTRACT' | 'SELL'
export type OrderStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

/**
 * Mode shape for `EXTRACT` orders. Three duration kinds:
 * - `"until_cancelled"` — runs until the player cancels.
 * - `{ ticks: N }` — runs for N ticks (counter on the order is incremented per tick).
 * - `{ until_full: true }` — runs until the ship's cargo cap is reached.
 */
export type ExtractMode = 'until_cancelled' | { ticks: number } | { until_full: true }

/** Params payload for the EXTRACT order kind. */
export type ExtractParams = {
    resourceKind: ResourceKind
    mode: ExtractMode
}

/** Params payload for the SELL order kind. */
export type SellParams = {
    resourceKind: ResourceKind
}

export type ShipOrderDto = {
    id: string
    shipId: string
    kind: OrderKind
    params: Record<string, unknown>
    status: OrderStatus
    /** Counter incremented by multi-tick handlers (currently only EXTRACT in `mode={ticks: N}`). */
    progressTicks: number
    createdAt: string
    startedAt: string | null
    completedAt: string | null
}

export type CreateOrderRequest = {
    kind: OrderKind
    params?: Record<string, unknown>
}
