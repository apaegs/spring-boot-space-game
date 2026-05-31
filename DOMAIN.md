# Domän-modell

Levande dokument för spelets kärn-domän. Uppdateras i **samma PR** som schema-ändringar — annars hamnar koden ur sync med dokumentationen.

> Statusen här är **v1-design** (issue #2). Öppna designfrågor diskuteras i issue-tråden tills de stängs.

## Översikt

Spelet är ett 2D rymd-explorer på en fix 100×100-grid. Varje spelare styr ett moderskepp som kan röra sig mellan rutor och interagera med planeter. Världen tickar i bakgrunden (≤ 1 min) och processar alla skepp i rörelse.

```
User (1) ─── (1..N) Ship           # 1 i v1, schemat tillåter fler
                     │
                     ▼ befinner sig på
              (x, y) på Grid (100×100)
                     │
                     ▼ kan landa på
                  Planet (pre-seedad)

WorldState (singleton)              # current_tick, last_tick_at, grid_width, grid_height
```

## Entiteter

### User

Autentiseringsidentitet — personen bakom kontot.

| Fält            | Typ          | Anteckning                  |
|-----------------|--------------|-----------------------------|
| id              | UUID         | PK                          |
| username        | VARCHAR(32)  | unique, case-insensitive    |
| email           | VARCHAR(255) | unique                      |
| password_hash   | VARCHAR(255) | BCrypt                      |
| created_at      | TIMESTAMPTZ  | default `now()`             |

### Ship

Moderskepp. I v1: exakt ett per User, säkerställt i applikationslogik (inte DB-constraint — se [Forward-compat](#forward-compat) nedan).

| Fält            | Typ          | Anteckning                                              |
|-----------------|--------------|---------------------------------------------------------|
| id              | UUID         | PK                                                      |
| user_id         | UUID         | FK → user.id, **inte unique** (för framtida fleet)      |
| name            | VARCHAR(64)  | spelar-valt eller genererat                             |
| x               | INT          | 0 ≤ x < grid_width                                      |
| y               | INT          | 0 ≤ y < grid_height                                     |
| destination_x   | INT NULL     | satt = i rörelse; null = står still                     |
| destination_y   | INT NULL     | samma constraint som destination_x (båda eller ingen)   |
| created_at      | TIMESTAMPTZ  | default `now()`                                         |

**Tick-beteende**: om `destination_x/y` är satt, flyttar varje tick skeppet 1 steg närmre destinationen (Chebyshev — 8-riktning, diagonalt räknas som 1 steg). När `(x, y) == (destination_x, destination_y)`, nollställs destinationen.

### Planet

Pre-seedad punkt av intresse på kartan.

| Fält         | Typ          | Anteckning                                |
|--------------|--------------|-------------------------------------------|
| id           | UUID         | PK                                        |
| x            | INT          | unique tillsammans med y                  |
| y            | INT          |                                           |
| name         | VARCHAR(64)  |                                           |
| description  | TEXT         |                                           |

Seedas i Flyway-migration (`V2__seed_planets.sql`). Initial planet-lista bestäms i issue #2-tråden.

### WorldState

Singleton-tabell. Bara en rad någonsin.

| Fält           | Typ          | Anteckning                              |
|----------------|--------------|------------------------------------------|
| id             | SMALLINT     | PK, CHECK (id = 1)                       |
| current_tick   | BIGINT       | ökar med 1 per tick                      |
| last_tick_at   | TIMESTAMPTZ  | senast processade tick                   |
| grid_width     | INT          | default 100                              |
| grid_height    | INT          | default 100                              |

## Vad som inte är med i v1

Med motivering — så vi vet *varför* något skjutits, inte bara *att* det är skjutet.

| Koncept              | Varför uppskjutet                                                                 | Hur det införs senare                                     |
|----------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------|
| **Fleet** (>1 skepp) | YAGNI för v1, men schemat är redo                                                 | App-logik öppnar för flera Ship-rader; ev. `is_mothership` |
| **Crew / besättning** | Inte del av v1-loopen                                                            | Ny `crew_member`-tabell, FK → ship.id                     |
| **Order-tabell**     | Endast MOVE finns; `Ship.destination_x/y` är "stående order"                      | Ny `orders`-tabell när BUILD/TRAIN/ATTACK införs          |
| **Resources, cargo** | Inte del av v1-loopen                                                             | Egen design-issue                                         |
| **Star** (entitet)   | Stjärnor är ren UI-dekoration tills de har gameplay-funktion                      | Lägg till tabell när de blir interaktiva                  |
| **Tile-tabell**      | 10 000 tomma rutor är onödigt att lagra                                           | Skapas vid behov om tiles får attribut (terräng, etc.)    |

## Forward-compat

`Ship.user_id` har **inte** unique-constraint trots att v1 har 1 skepp per User. Anledning: när fleet införs ska det vara en ren applikationsändring (lyft constraint i koden) — inte en migration. DB-constraints är dyra att backtracka.

API:t `GET /api/ship` (singular) accepterar URL-breaking change till `/api/ships` när fleet införs, eftersom inga externa konsumenter finns.

## Process: lägga till nytt domän-koncept

1. Öppna ett **design-issue** först. Bestäm fält, relationer och vad som *inte* ska med.
2. Uppdatera DOMAIN.md i samma PR som Flyway-migrationen och JPA-entiteten — annars hamnar dokumentationen ur sync.
3. Om termen är ny: uppdatera även [CLAUDE.md](CLAUDE.md):s domän-vokabulär.
4. Posta slutgiltigt schema som kommentar i design-issuet och stäng det.

## Öppna designfrågor (v1)

Diskuteras i issue #2:

1. 4- eller 8-riktnings rörelse? *Rek: 8-riktning (Chebyshev).*
2. Auto-landning på planet-tile eller separat `LAND`-order? *Rek: auto-landning.*
3. Antal planeter och placering? Konkret seed-data.
4. Skepp-namn: spelar-valt eller genererat?
5. Kollision: kan två skepp dela tile? *Rek: ja, ingen kollision i v1.*
