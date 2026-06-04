# Domain Model

Living document for the game's core domain. Updated in the **same PR** as any schema change — otherwise the code drifts out of sync with the docs.

> Status here is **v1 design** (issue #2). Open design questions are discussed in the issue thread until they're closed.

## Overview

The game is a 2D space explorer on a fixed 100×100 grid. Each player controls a mothership that can move between tiles and interact with celestial bodies (planets, asteroids, gas giants, stars). The world ticks in the background (≤ 1 min) and processes any orders queued on ships.

Players queue a sequence of orders (MOVE, EXTRACT, SELL) on their ship, log out, and the world advances those orders one step per tick while they're away. A ship Chebyshev-adjacent to a body is automatically `ORBITING` — no explicit landing.

```text
User (1) ─── (1..N) Ship           # 1 in v1, schema allows more
                     │  └─── (0..N) ShipOrder       # queue of pending/active orders
                     │
                     ▼ located at
              (x, y) on Grid (100×100)
                     │
                     ▼ ORBITING when Chebyshev-adjacent to
              CelestialBody (pre-seeded; kind ∈ {ROCKY_PLANET, LAVA_PLANET, ICE_PLANET, GAS_GIANT, ASTEROID, STAR})

WorldState (singleton)              # current_tick, last_tick_at
```

## Entities

### User

Authentication identity — the person behind the account.

| Field           | Type         | Notes                                                                  |
|-----------------|--------------|------------------------------------------------------------------------|
| id              | UUID         | PK                                                                     |
| username        | VARCHAR(32)  | unique, case-insensitive                                               |
| email           | VARCHAR(255) | unique                                                                 |
| password_hash   | VARCHAR(255) | BCrypt                                                                 |
| credits         | BIGINT       | in-game currency; earned by SELL handler. Starts at 0. Added in V8.    |
| created_at      | TIMESTAMPTZ  | default `now()`                                                        |

### Ship

A player-controlled vessel. **A User can have any number of Ships** (the auto-created mothership at registration plus any created via `POST /api/ships`). Historic note: v1 originally constrained this to exactly one; the constraint was lifted in #32 when the multi-ship UI shipped.

| Field           | Type         | Notes                                                                  |
|-----------------|--------------|------------------------------------------------------------------------|
| id              | UUID         | PK                                                                     |
| user_id         | UUID         | FK → user.id (not unique — many ships per user)                        |
| name            | VARCHAR(64)  | player-chosen or generated                                             |
| x               | INT          | 0 ≤ x < 100 (see `WorldConstants.GRID_SIZE`)                           |
| y               | INT          | 0 ≤ y < 100 (see `WorldConstants.GRID_SIZE`)                           |
| ship_type_id    | UUID         | FK → ship_types.id. v1 always references `MOTHERSHIP`. Added in V8.    |
| created_at      | TIMESTAMPTZ  | default `now()`                                                        |

**Ownership**: every ship-scoped *action* endpoint (`GET /api/ships/{id}/...`, `POST /api/ships/{id}/orders`, etc.) checks that the `{shipId}` belongs to the caller via `ShipService.requireOwnedShip`. A non-owned ship ID 404s — deliberately indistinguishable from "ship doesn't exist" so the API doesn't leak other users' ship IDs through the action surface.

**Public visibility**: a separate, narrower projection — `PublicShipDto` (id, name, x, y) — is exposed via `GET /api/world/ships` so the map can render every player's ships. No owner identity, no audit timestamps. Public ids are knowable; they just can't be used to *do* anything against ships you don't own (the action endpoints still 404). Introduced in #35 when foreign ships became visible on the shared map.

**Position vs. orders**: a ship is at a single tile `(x, y)`. What it's currently *doing* lives in the order queue (see `ShipOrder` below), not on the ship row. Earlier v1 drafts had `destination_x/y` columns directly on the ship; those were dropped in V4 when the orders queue replaced them.

**Spawn**: every new ship — auto-created or via `POST /api/ships` — prefers `(51, 50)` (see `ShipService.SPAWN_X/Y`). When that tile is taken (per-tile collision rule, see below), the spawn-spiral walks outward in Chebyshev rings up to radius 10 (441 candidate tiles around Earth) until a free tile is found. Past the cap → 503. The deterministic ring walk is `(dy, dx)` order, so the second contested spawn lands at `(50, 49)`, third at `(51, 49)`, fourth at `(52, 49)`. Earth at `(50, 50)` is skipped by the body-occupancy check during the walk.

**Tile occupancy**: at most one entity (ship or celestial body) per tile (issue #88). Enforced at three layers:
- Schema: `UNIQUE(x, y)` on `ships` (`ships_xy_unique`, V12) and on `celestial_bodies` (`celestial_bodies_xy_unique`, V8).
- API: `POST /api/ships/{id}/orders` with a `MOVE` to an occupied destination returns 400. Spawn collisions on the cross-transaction race retry the spiral and translate any `ships_xy_unique` violation into a fresh search.
- Tick: a MOVE whose next intermediate tile is occupied cancels the order with `"blocked at (X, Y) — replan"`. No pause/wait — pausing would deadlock against stationary obstacles. A* pathfinding (#45) can revisit this decision.

**Name**: auto-generated as `"<username>'s ship"` for the first ship and `"<username>'s ship N"` for the Nth additional ship (N = current ship count + 1). `POST /api/ships` accepts an optional `name` in the body to override the auto-name.

### ShipOrder

A pending or completed instruction in a ship's queue. The game loop pulls the oldest non-finished order per ship each tick and processes one step toward its completion.

| Field         | Type         | Notes                                                                |
|---------------|--------------|----------------------------------------------------------------------|
| id            | UUID         | PK                                                                   |
| ship_id       | UUID         | FK → ship.id, `ON DELETE CASCADE`                                    |
| kind          | VARCHAR(32)  | `'MOVE'`, `'EXTRACT'`, `'SELL'`, … extensible. Validated at the application layer |
| params        | JSONB        | order-type-specific payload (e.g. `{ "x": 50, "y": 50 }` for MOVE)   |
| status        | VARCHAR(16)  | `PENDING` → `ACTIVE` → `COMPLETED` (or `CANCELLED`)                  |
| created_at    | TIMESTAMPTZ  | default `now()` — drives queue order                                 |
| started_at    | TIMESTAMPTZ  | set when status → ACTIVE                                             |
| completed_at  | TIMESTAMPTZ  | set when status → COMPLETED                                          |

**Queue semantics**: orders are processed FIFO per ship (`ORDER BY created_at`). At any moment, a ship has at most one `ACTIVE` order and any number of `PENDING` orders behind it. Completed orders stay in the table for audit/history (cleanup is deferred).

**Why JSONB for params**: order types are intentionally extensible. WAIT needs `{ ticks }`, SCAN needs `{ radius }`, MINE needs `{ resourceType }`. Adding a new order type is a new `OrderHandler` strategy class + a new `kind` value — no schema change.

**v1 order types**:

| Kind    | Params                                                                                                      | Behavior                                                                                                                                                                                       |
|---------|-------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MOVE    | `{ "x": int, "y": int }`                                                                                    | Each tick, advance one Chebyshev step toward `(x, y)`. Complete on arrival. Rejected at queue time (400) if the destination is occupied by a ship or a body; cancels mid-flight if the next step's tile becomes occupied. |
| EXTRACT | `{ "resourceKind": ResourceKind, "mode": "until_cancelled" \| { "ticks": N } \| { "until_full": true } }` | Per tick, extract `min(extract_rate, reserve, cap-current)` units of the resource into `ship_cargo` from the first Chebyshev-adjacent body. Terminates per `mode`. Cancels if not `ORBITING`. |
| SELL    | `{ "resourceKind": ResourceKind }`                                                                          | One tick. Convert all cargo of `resourceKind` to credits at the first adjacent body's buy price. Deletes the cargo row. Cancels if not `ORBITING`, the body doesn't buy this resource, or the ship has none. |

**No auto-prerequisite middleware**. EXTRACT and SELL queued from a non-`ORBITING`
position simply cancel at tick time with a clear reason — the player MOVEs
adjacent to the target body themselves first. (Earlier `LAND` / `TAKE_OFF`
auto-injection — and the order kinds themselves — were removed in #87.)

`ship_orders.progress_ticks` is a per-order counter incremented by multi-tick
handlers (currently only `EXTRACT` in `mode={ticks: N}`) every tick they make
progress. Plain `INT` column added in V10 so Hibernate's dirty checking
catches updates reliably — mutating the `params` JSONB Map in place is
fragile under Hibernate's JSON handling. The `auto_inserted` column added
alongside it was dropped in V11 when the middleware that wrote `TRUE` to it
went away.

### CelestialBody

Pre-seeded point of interest on the grid — planets, asteroids, gas giants, and stars. Replaces the v1 `Planet` entity (table renamed in V8). The thing a ship is `ORBITING` when adjacent.

| Field        | Type         | Notes                                                       |
|--------------|--------------|-------------------------------------------------------------|
| id           | UUID         | PK                                                          |
| x            | INT          | unique together with y, `0 ≤ x < 100`                       |
| y            | INT          | `0 ≤ y < 100`                                               |
| name         | VARCHAR(64)  | display name                                                |
| description  | TEXT         | flavor text                                                 |
| kind         | VARCHAR(32)  | `CelestialBodyKind`; one of the taxonomy below. Added in V8.|

**Kind taxonomy** (`CelestialBodyKind`). Kind is descriptive — it drives the seeded yield/buy matrix in V9 but doesn't change derivation rules. Every kind that has reserves is reachable through `ORBITING` (Chebyshev-adjacent) the same way.

| Kind           | Notes                                                                  |
|----------------|------------------------------------------------------------------------|
| `ROCKY_PLANET` | Common. Iron-rich, trace water and rare metal.                         |
| `LAVA_PLANET`  | Rare. Heavy rare-metal yield.                                          |
| `ICE_PLANET`   | Water-heavy.                                                           |
| `GAS_GIANT`    | Hydrogen + helium atmosphere.                                          |
| `ASTEROID`     | Many on the map. Mixed iron/water/rare-metal trace.                    |
| `STAR`         | Decorative + nav landmark. No extraction.                              |

Seeded in `V9__seed_initial_map.sql` — ~40 bodies across the kind taxonomy. The seed replaces the original V5 6-planet seed entirely. Earth still sits at `(50, 50)`; spawn is `(51, 50)`, one tile east, so a fresh player's mothership starts Chebyshev-adjacent to its home body and derives `ORBITING` immediately.

### BodyResource

Per-body reserve of a single resource. Composite PK `(body_id, resource_kind)`. The EXTRACT handler (PR 2) decrements `reserve` within the tick transaction. Absence of a row means the body lacks that resource at all — distinct from `reserve = 0` ("depleted but used to have").

| Field         | Type         | Notes                                              |
|---------------|--------------|----------------------------------------------------|
| body_id       | UUID         | FK → celestial_bodies.id, `ON DELETE CASCADE`      |
| resource_kind | VARCHAR(16)  | `ResourceKind` enum value                          |
| reserve       | INT          | `CHECK (reserve >= 0)`                             |

### BodyBuyPrice

Per-body buy price for a single resource. Composite PK `(body_id, resource_kind)`. Absence of a row = body doesn't buy this resource (the SELL handler in PR 2 will cancel with that reason).

| Field          | Type         | Notes                                              |
|----------------|--------------|----------------------------------------------------|
| body_id        | UUID         | FK → celestial_bodies.id, `ON DELETE CASCADE`      |
| resource_kind  | VARCHAR(16)  | `ResourceKind` enum value                          |
| price_per_unit | INT          | `CHECK (price_per_unit > 0)`                       |

### ResourceKind

Application-layer enum (not a DB table) catalogue of resources a ship can carry and a body can yield or buy. All resources extract from `ORBITING` — which bodies yield which is governed entirely by the seeded `body_resources` matrix.

| Kind         | Source examples            | Sink examples           |
|--------------|----------------------------|-------------------------|
| `IRON`       | Rocky planets, asteroids   | Industrial bodies       |
| `WATER`      | Ice planets, asteroids     | Habitat bodies          |
| `HYDROGEN`   | Gas giants                 | Habitat/fuel buyers     |
| `HELIUM`     | Gas giants                 | Tech buyers             |
| `RARE_METAL` | Lava planets, asteroids    | Tech buyers             |

### ShipType

Catalog of ship types. Stats per type (cargo capacity, extraction rate, future: speed/fuel/hull) live here rather than on each ship, so adding a new type is a row, not a code change.

| Field          | Type         | Notes                                            |
|----------------|--------------|--------------------------------------------------|
| id             | UUID         | PK                                               |
| code           | VARCHAR(32)  | unique catalog code (e.g. `MOTHERSHIP`)          |
| name           | VARCHAR(64)  | display name                                     |
| cargo_capacity | INT          | total cargo cap across all resources             |
| extract_rate   | INT          | units per tick the EXTRACT handler can pull      |
| created_at     | TIMESTAMPTZ  | default `now()`                                  |

v1 seed: one row, `MOTHERSHIP` (`cargo_capacity = 500`, `extract_rate = 10`) with the deterministic UUID `00000000-0000-0000-0000-000000000001` so `ships.ship_type_id` can backfill against it in V8.

### ShipCargo

Per-ship per-resource cargo row. Composite PK `(ship_id, resource_kind)`. The EXTRACT handler (PR 2) upserts rows; SELL removes them. Cargo cap is enforced against `SUM(qty)` for the ship — total units across all resources combined.

| Field         | Type         | Notes                                              |
|---------------|--------------|----------------------------------------------------|
| ship_id       | UUID         | FK → ships.id, `ON DELETE CASCADE`                 |
| resource_kind | VARCHAR(16)  | `ResourceKind` enum value                          |
| qty           | INT          | `CHECK (qty >= 0)`                                 |

### WorldState

Singleton table. Only ever one row.

| Field          | Type         | Notes                                    |
|----------------|--------------|------------------------------------------|
| id             | SMALLINT     | PK, CHECK (id = 1)                       |
| current_tick   | BIGINT       | increments by 1 per tick                 |
| last_tick_at   | TIMESTAMPTZ  | most recently processed tick             |

**Grid size**: fixed at 100×100 forever in v1 — the single source of truth is `WorldConstants.GRID_SIZE` (see issue #29). The CHECK constraints on `ships(x,y)` and `celestial_bodies(x,y)` use the same literal at the schema layer. Per-instance grid sizing would require moving the value back onto `world_state` and replacing the CHECK constraints with triggers; not planned.

**Tick interval**: configured via `game.tick.interval-ms` in `application.properties`. v1 value is `3000` (3 s) — the same number for every deployment and every player, since tick pace is a game-design choice, not a per-environment tuning knob.

**Tick concurrency**: v1 runs a single application instance and the `@Scheduled` framework serializes calls within a JVM, so two ticks can't overlap. The increment is also a single SQL `UPDATE`, so even if a future bug triggers it concurrently the row never goes missing. Horizontal scaling will need cross-node coordination (Shedlock or similar) — deferred.

**Tick hook**: `TickService.advanceTick()` publishes a `TickEvent` via Spring's `ApplicationEventPublisher`. Per-tick game logic listens via `@EventListener` — currently `ShipOrderTickListener` (dispatches to `ShipTickProcessor.processOneShip` per ship). New per-tick subsystems plug in by adding a listener; `TickService` never has to change.

## Not in v1

With justification — so we know *why* something was deferred, not just *that* it was.

| Concept                              | Why deferred                                                                      | How it's introduced later                                       |
|--------------------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------|
| **Crew**                             | Not part of the v1 loop                                                           | New `crew_member` table, FK → ship.id                           |
| **Ship speed**                       | All ships move 1 tile/tick in v1                                                  | New `speed` column on `ship`; MOVE handler loops `speed` times  |
| **Ship type stats beyond cargo/extract** | One ship class (`MOTHERSHIP`) in v1; speed/fuel/hull land as new columns on `ship_types` when needed | Add columns to `ship_types` + handlers that read them          |
| **More order kinds** (WAIT/SCAN/ATTACK/…) | Schema + dispatcher are ready; just need handler classes | New `OrderHandler` impl per kind + entry in the order-kind whitelist |
| **Completed-order cleanup**          | History stays in `ship_orders` forever in v1; not enough rows to matter           | Scheduled archive job, or partitioning by `completed_at`        |
| **Procgen map**                      | V9 hand-seeds ~40 bodies for v1; procgen produces the same body+resource model from a seed source | See #47 — same model, different seed source.        |
| **Star** (entity)                    | Stars are pure UI decoration until they have gameplay function                    | Add a table when they become interactive                        |
| **Tile table**                       | 10,000 empty cells aren't worth storing                                           | Created if/when tiles get attributes (terrain, etc.)            |
| **Sprite art**                       | v1 renders geometric primitives (cyan triangle for ship, amber circles for bodies) so the map works without an art pass | Native sprite size will be **16×16**, rendered at integer multiples (no downscale — pixel art breaks). Whole 100×100 grid at native = 1600×1600 canvas, so when sprites arrive the map switches from "fit everything" to a camera that follows the ship. See `WorldMap.ts` — swap `Graphics` for `Sprite.from(...)`. |

## Forward-compat

`ShipOrder.kind` is `VARCHAR` (not an `ENUM` type) so adding a new order kind is a code-only change. Validation happens at the application layer against the set of registered `OrderHandler` strategies. (The original "Ship.user_id is not unique so multi-ship is a code change later" forward-compat note has been **fulfilled** — multi-ship landed in #32, and the absent constraint is what made it possible without a migration.)

## Process: adding a new domain concept

1. Open a **design issue** first. Decide fields, relationships, and what's *not* in scope.
2. Update DOMAIN.md in the same PR as the Flyway migration and JPA entity — otherwise the docs drift out of sync.
3. If the term is new: update the domain vocabulary in [CLAUDE.md](CLAUDE.md) as well.
4. Post the final schema as a comment on the design issue and close it.

## Open design questions (v1)

Originally raised in issue #2; some are now resolved by later issues.

1. ~~4- or 8-direction movement?~~ **Resolved**: 8-direction (Chebyshev). See ShipOrder.MOVE.
2. ~~Auto-landing on a planet tile, or a separate `LAND` order?~~ **Resolved in #11, superseded in #87**: the orbit-only model collapsed `LANDED`/`ORBITING`/`LAND`/`TAKE_OFF` to a single position-derived `ORBITING` (ship Chebyshev-adjacent to any body) — no order needed.
3. ~~Number of planets and placement?~~ **Resolved in #46 + PR 1 of #80**: 40 hand-placed celestial bodies across the kind taxonomy in `V9__seed_initial_map.sql` (replaces the original V5 6-planet seed).
4. Ship name: player-chosen or generated? Currently generated (`"<username>'s ship"`). Player-chosen deferred until there's a UI to choose.
5. Collision: can two ships share a tile? *Rec: yes, no collision in v1.*
