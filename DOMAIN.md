# Domain Model

Living document for the game's core domain. Updated in the **same PR** as any schema change — otherwise the code drifts out of sync with the docs.

> Status here is **v1 design** (issue #2). Open design questions are discussed in the issue thread until they're closed.

## Overview

The game is a 2D space explorer on a fixed 100×100 grid. Each player controls a mothership that can move between tiles and interact with planets. The world ticks in the background (≤ 1 min) and processes any ships in motion.

```text
User (1) ─── (1..N) Ship           # 1 in v1, schema allows more
                     │
                     ▼ located at
              (x, y) on Grid (100×100)
                     │
                     ▼ can land on
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

Mothership. In v1: exactly one per User, enforced in application logic (not as a DB constraint — see [Forward-compat](#forward-compat) below).

| Field           | Type         | Notes                                                   |
|-----------------|--------------|---------------------------------------------------------|
| id              | UUID         | PK                                                      |
| user_id         | UUID         | FK → user.id, **not unique** (for future fleet support) |
| name            | VARCHAR(64)  | player-chosen or generated                              |
| x               | INT          | 0 ≤ x < grid_width                                      |
| y               | INT          | 0 ≤ y < grid_height                                     |
| destination_x   | INT NULL     | set = in motion; null = stationary                      |
| destination_y   | INT NULL     | same constraint as destination_x (both or neither)      |
| created_at      | TIMESTAMPTZ  | default `now()`                                         |

**Tick behavior**: if `destination_x/y` is set, each tick advances the ship 1 step toward the destination (Chebyshev — 8-direction, diagonal counts as 1 step). When `(x, y) == (destination_x, destination_y)`, the destination is cleared.

### Planet

Pre-seeded point of interest on the map.

| Field        | Type         | Notes                                     |
|--------------|--------------|-------------------------------------------|
| id           | UUID         | PK                                        |
| x            | INT          | unique together with y                    |
| y            | INT          |                                           |
| name         | VARCHAR(64)  |                                           |
| description  | TEXT         |                                           |

Seeded in a Flyway migration (`V2__seed_planets.sql`). Initial planet list is decided in the issue #2 thread.

### WorldState

Singleton table. Only ever one row.

| Field          | Type         | Notes                                    |
|----------------|--------------|------------------------------------------|
| id             | SMALLINT     | PK, CHECK (id = 1)                       |
| current_tick   | BIGINT       | increments by 1 per tick                 |
| last_tick_at   | TIMESTAMPTZ  | most recently processed tick             |
| grid_width     | INT          | default 100                              |
| grid_height    | INT          | default 100                              |

## Not in v1

With justification — so we know *why* something was deferred, not just *that* it was.

| Concept              | Why deferred                                                                      | How it's introduced later                                 |
|----------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------|
| **Fleet** (>1 ship)  | YAGNI for v1, but the schema is ready                                             | App logic allows multiple Ship rows; maybe `is_mothership` |
| **Crew**             | Not part of the v1 loop                                                           | New `crew_member` table, FK → ship.id                     |
| **Order table**      | Only MOVE exists; `Ship.destination_x/y` is the "standing order"                  | New `orders` table once BUILD/TRAIN/ATTACK are added      |
| **Resources, cargo** | Not part of the v1 loop                                                           | Its own design issue                                      |
| **Star** (entity)    | Stars are pure UI decoration until they have gameplay function                    | Add a table when they become interactive                  |
| **Tile table**       | 10,000 empty cells aren't worth storing                                           | Created if/when tiles get attributes (terrain, etc.)      |

## Forward-compat

`Ship.user_id` has **no** unique constraint even though v1 has 1 ship per User. Reason: when fleet support arrives, it should be a pure application-logic change (lift the constraint in code) — not a migration. DB constraints are expensive to walk back.

The `GET /api/ship` (singular) endpoint accepts a URL-breaking change to `/api/ships` when fleet arrives, since there are no external consumers.

## Process: adding a new domain concept

1. Open a **design issue** first. Decide fields, relationships, and what's *not* in scope.
2. Update DOMAIN.md in the same PR as the Flyway migration and JPA entity — otherwise the docs drift out of sync.
3. If the term is new: update the domain vocabulary in [CLAUDE.md](CLAUDE.md) as well.
4. Post the final schema as a comment on the design issue and close it.

## Open design questions (v1)

Discussed in issue #2:

1. 4- or 8-direction movement? *Rec: 8-direction (Chebyshev).*
2. Auto-landing on a planet tile, or a separate `LAND` order? *Rec: auto-landing.*
3. Number of planets and placement? Concrete seed data.
4. Ship name: player-chosen or generated?
5. Collision: can two ships share a tile? *Rec: yes, no collision in v1.*
