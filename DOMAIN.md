# Domain Model

Living document for the game's core domain. Updated in the **same PR** as any schema change — otherwise the code drifts out of sync with the docs.

> Status here is **v1 design** (issue #2). Open design questions are discussed in the issue thread until they're closed.

## Overview

The game is a 2D space explorer on a fixed 100×100 grid. Each player controls a mothership that can move between tiles and interact with planets. The world ticks in the background (≤ 1 min) and processes any orders queued on ships.

Players queue a sequence of orders (MOVE, LAND, …) on their ship, log out, and the world advances those orders one step per tick while they're away.

```text
User (1) ─── (1..N) Ship           # 1 in v1, schema allows more
                     │  └─── (0..N) ShipOrder       # queue of pending/active orders
                     │
                     ▼ located at
              (x, y) on Grid (100×100)
                     │
                     ▼ can LAND on
                  Planet (pre-seeded)

WorldState (singleton)              # current_tick, last_tick_at, grid_width, grid_height
```

## Entities

### User

Authentication identity — the person behind the account.

| Field           | Type         | Notes                       |
|-----------------|--------------|-----------------------------|
| id              | UUID         | PK                          |
| username        | VARCHAR(32)  | unique, case-insensitive    |
| email           | VARCHAR(255) | unique                      |
| password_hash   | VARCHAR(255) | BCrypt                      |
| created_at      | TIMESTAMPTZ  | default `now()`             |

### Ship

A player-controlled vessel. **A User can have any number of Ships** (the auto-created mothership at registration plus any created via `POST /api/ships`). Historic note: v1 originally constrained this to exactly one; the constraint was lifted in #32 when the multi-ship UI shipped.

| Field           | Type         | Notes                                                   |
|-----------------|--------------|---------------------------------------------------------|
| id              | UUID         | PK                                                      |
| user_id         | UUID         | FK → user.id (not unique — many ships per user)         |
| name            | VARCHAR(64)  | player-chosen or generated                              |
| x               | INT          | 0 ≤ x < grid_width                                      |
| y               | INT          | 0 ≤ y < grid_height                                     |
| created_at      | TIMESTAMPTZ  | default `now()`                                         |

**Ownership**: every ship-scoped endpoint (`GET /api/ships/{id}/...`, `POST /api/ships/{id}/orders`, etc.) checks that the `{shipId}` belongs to the caller via `ShipService.requireOwnedShip`. A non-owned ship ID 404s — deliberately indistinguishable from "ship doesn't exist" so the API doesn't leak other users' ship IDs.

**Position vs. orders**: a ship is at a single tile `(x, y)`. What it's currently *doing* lives in the order queue (see `ShipOrder` below), not on the ship row. Earlier v1 drafts had `destination_x/y` columns directly on the ship; those were dropped in V4 when the orders queue replaced them.

**Spawn**: every new ship — auto-created or via `POST /api/ships` — starts at `(50, 50)` (see `ShipService.SPAWN_X/Y`). Hard-coded for v1 — deterministic for tests and a single source of truth. Earth is also seeded at `(50, 50)` so a fresh player's mothership starts standing on their home planet.

**Name**: auto-generated as `"<username>'s ship"` for the first ship and `"<username>'s ship N"` for the Nth additional ship (N = current ship count + 1). `POST /api/ships` accepts an optional `name` in the body to override the auto-name.

### ShipOrder

A pending or completed instruction in a ship's queue. The game loop pulls the oldest non-finished order per ship each tick and processes one step toward its completion.

| Field         | Type         | Notes                                                                |
|---------------|--------------|----------------------------------------------------------------------|
| id            | UUID         | PK                                                                   |
| ship_id       | UUID         | FK → ship.id, `ON DELETE CASCADE`                                    |
| kind          | VARCHAR(32)  | `'MOVE'`, `'LAND'`, … extensible. Validated at the application layer |
| params        | JSONB        | order-type-specific payload (e.g. `{ "x": 50, "y": 50 }` for MOVE)   |
| status        | VARCHAR(16)  | `PENDING` → `ACTIVE` → `COMPLETED` (or `CANCELLED`)                  |
| created_at    | TIMESTAMPTZ  | default `now()` — drives queue order                                 |
| started_at    | TIMESTAMPTZ  | set when status → ACTIVE                                             |
| completed_at  | TIMESTAMPTZ  | set when status → COMPLETED                                          |

**Queue semantics**: orders are processed FIFO per ship (`ORDER BY created_at`). At any moment, a ship has at most one `ACTIVE` order and any number of `PENDING` orders behind it. Completed orders stay in the table for audit/history (cleanup is deferred).

**Why JSONB for params**: order types are intentionally extensible. WAIT needs `{ ticks }`, SCAN needs `{ radius }`, MINE needs `{ resourceType }`. Adding a new order type is a new `OrderHandler` strategy class + a new `kind` value — no schema change.

**v1 order types**:

| Kind  | Params                  | Behavior                                                                 |
|-------|-------------------------|--------------------------------------------------------------------------|
| MOVE  | `{ "x": int, "y": int }` | Each tick, advance one Chebyshev step toward `(x, y)`. Complete on arrival. |
| LAND  | `{}`                    | Validate that ship is on a planet tile. If yes, complete in one tick. If no, fail and CANCEL with reason. |

### Planet

Pre-seeded point of interest on the map. Only meaningful target for the LAND order in v1.

| Field        | Type         | Notes                                     |
|--------------|--------------|-------------------------------------------|
| id           | UUID         | PK                                        |
| x            | INT          | unique together with y, `0 ≤ x < 100`     |
| y            | INT          | `0 ≤ y < 100`                             |
| name         | VARCHAR(64)  | display name                              |
| description  | TEXT         | flavor text                               |

Seeded in `V5__create_planets.sql`. The v1 seed includes Earth at `(50, 50)` (the spawn tile) and a handful of others scattered across the grid — enough to give early players concrete targets to MOVE/LAND on.

### WorldState

Singleton table. Only ever one row.

| Field          | Type         | Notes                                    |
|----------------|--------------|------------------------------------------|
| id             | SMALLINT     | PK, CHECK (id = 1)                       |
| current_tick   | BIGINT       | increments by 1 per tick                 |
| last_tick_at   | TIMESTAMPTZ  | most recently processed tick             |
| grid_width     | INT          | default 100                              |
| grid_height    | INT          | default 100                              |

**Tick interval**: configured via `game.tick.interval-ms` in `application.properties`. v1 value is `3000` (3 s) — the same number for every deployment and every player, since tick pace is a game-design choice, not a per-environment tuning knob.

**Tick concurrency**: v1 runs a single application instance and the `@Scheduled` framework serializes calls within a JVM, so two ticks can't overlap. The increment is also a single SQL `UPDATE`, so even if a future bug triggers it concurrently the row never goes missing. Horizontal scaling will need cross-node coordination (Shedlock or similar) — deferred.

**Tick hook**: `TickService.advanceTick()` publishes a `TickEvent` via Spring's `ApplicationEventPublisher`. Per-tick game logic listens via `@EventListener` — currently `ShipOrderTickListener` (dispatches to `ShipTickProcessor.processOneShip` per ship). New per-tick subsystems plug in by adding a listener; `TickService` never has to change.

## Not in v1

With justification — so we know *why* something was deferred, not just *that* it was.

| Concept                              | Why deferred                                                                      | How it's introduced later                                       |
|--------------------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------|
| **Crew**                             | Not part of the v1 loop                                                           | New `crew_member` table, FK → ship.id                           |
| **Ship speed**                       | All ships move 1 tile/tick in v1                                                  | New `speed` column on `ship`; MOVE handler loops `speed` times  |
| **Ship types & capabilities**        | One ship class in v1; later "scout can SCAN, miner can MINE, freighter cannot"    | Add `ship_type` (FK or enum) + per-type allowed `OrderHandler` list |
| **More order kinds** (WAIT/SCAN/MINE/TRADE/ATTACK/…) | Schema + dispatcher are ready; just need handler classes | New `OrderHandler` impl per kind + entry in the order-kind whitelist |
| **Completed-order cleanup**          | History stays in `ship_orders` forever in v1; not enough rows to matter           | Scheduled archive job, or partitioning by `completed_at`        |
| **Resources, cargo**                 | Not part of the v1 loop                                                           | Its own design issue                                            |
| **Star** (entity)                    | Stars are pure UI decoration until they have gameplay function                    | Add a table when they become interactive                        |
| **Tile table**                       | 10,000 empty cells aren't worth storing                                           | Created if/when tiles get attributes (terrain, etc.)            |
| **Sprite art**                       | v1 renders geometric primitives (cyan triangle for ship, amber circles for planets) so the map works without an art pass | Native sprite size will be **16×16**, rendered at integer multiples (no downscale — pixel art breaks). Whole 100×100 grid at native = 1600×1600 canvas, so when sprites arrive the map switches from "fit everything" to a camera that follows the ship. See `WorldMap.ts` — swap `Graphics` for `Sprite.from(...)`. |

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
2. ~~Auto-landing on a planet tile, or a separate `LAND` order?~~ **Resolved in #11**: explicit LAND order, queued alongside MOVE. Lets players script "fly to Mars, then land" as a single chain.
3. ~~Number of planets and placement?~~ **Resolved in #11**: 6 hand-placed planets in `V5__seed_planets.sql`.
4. Ship name: player-chosen or generated? Currently generated (`"<username>'s ship"`). Player-chosen deferred until there's a UI to choose.
5. Collision: can two ships share a tile? *Rec: yes, no collision in v1.*
