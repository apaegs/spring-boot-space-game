# CLAUDE.md

This file is for two readers: **the friend being onboarded** and **Claude the agent**. Both should be able to read it and start working in the project without asking.

## What the project is

A 2D, tick-based space game in the browser — closest comparison is a web-based relative of Elite. Each player controls a mothership on a fixed 100×100 grid with planets and stars. The player sets a destination, the ship moves 1 tile per tick, and on arrival at a planet the player can land and interact. The world ticks in the background (≤ 1 min) regardless of whether anyone is logged in — it's not real-time strategy, more "log in now and then and make decisions."

Detailed domain model: see [DOMAIN.md](DOMAIN.md).

## Run the project

```sh
./mvnw spring-boot:run          # Starts the app + Postgres (via docker-compose-auto)
./mvnw verify                   # Builds + runs tests
./mvnw test                     # Tests only
docker compose up -d            # Postgres only (if you don't want to run the app)
```

App on http://localhost:8080.

## Frontend

- **React + Vite** in `frontend/`. Backend exposes REST under `/api/**`.
- **PixiJS** for the 2D map — a `<canvas>` mounted in a React component. Pixi code lives isolated from the React tree (Pixi owns its own render loop); React only syncs state in via props/refs and reads events back via callbacks.
- **Not** Phaser — overkill for tick-based.
- Animated numbers via a lightweight lib (e.g. `framer-motion` or `react-spring`); no reason to write your own easing functions.
- State: start with React state + `@tanstack/react-query` for server state. Don't reach for Redux/Zustand before it's actually needed.

## Code conventions

### Package structure
Package **by domain**, not by technical layer:
```text
org.example.springbootspacegame
├── ship/           # Entity, Repository, Service, Controller for Ship
├── planet/         # ... for Planet
├── world/          # WorldState + grid-related logic
├── tick/           # Tick scheduler
└── auth/           # User, login, registration
```
Not `controllers/`, `services/`, `repositories/` at the top level.

### Layers
- **Controller**: thin. Takes a DTO, calls the Service, returns a DTO. No business logic.
- **Service**: all business logic. Transaction boundaries live here (`@Transactional`).
- **Repository**: Spring Data interfaces. No custom `@Query` if derived names suffice.
- **Entity vs DTO**: never expose JPA entities through REST. Map explicitly to DTO in the Service layer.

### Lombok
Use `@RequiredArgsConstructor` for constructor injection and `@Getter`/`@Setter` sparingly. **Not** `@Data` on entities (equals/hashCode on JPA entities is a trap).

### Test naming
`<ClassUnderTest>Test` for unit, `<ClassUnderTest>IT` for integration. Integration tests use Testcontainers — **never mock the database**.

### Integration test setup
Annotate `*IT` classes with `@IntegrationTest` (defined in `src/test/java/.../IntegrationTest.java`). The meta-annotation bundles `@SpringBootTest`, `@Import(TestcontainersConfiguration.class)`, and a `@Sql` that truncates per-test tables before every test method — so tests don't leak state into each other. If a new table is added, list it in `src/test/resources/cleanup.sql` (or rely on `CASCADE` from a FK to an already-listed table).

### Migrations
Schema changes go **always** through Flyway. Files in `src/main/resources/db/migration/` with the format `V<n>__<description>.sql`. Never `spring.jpa.hibernate.ddl-auto=update` — it's on `validate` for a reason.

## Workflow

### Branches
- `feature/<short-description>` for new features
- `fix/<short-description>` for bug fixes
- `chore/<short-description>` for cleanup, deps, CI
- Never push directly to `main`

### Commits
Free-form text in English, but keep each commit focused on one thing. Imperative mood ("Add tick scheduler", not "Added" or "Adds"). The body may explain *why* if it isn't obvious.

### PRs
- A template (`.github/pull_request_template.md`) pre-fills Summary / Why / Test plan when you open a PR — use it, don't delete sections.
- Link the issue: `Closes #N`
- Description should say *why*, not just *what* (the diff shows what)
- Keep them small (< ~400 lines of diff). Large PRs are hard to review seriously — split them.
- One review required. CI must be green.
- Squash and merge.

### Issues
- `.github/ISSUE_TEMPLATE/` has two templates: **Bug** (what happened, expected, repro) and **Feature** (Mål, Omfattning, Acceptans — same format as #2/#3/#4).
- Pick the matching template when filing. Blank issues still work but the template is the default path.

## Secrets and environment variables

- **Anything sensitive goes in `.env`** (gitignored). Template: `.env.example` (committed, no real values).
- Spring reads `.env` automatically via `spring.config.import=optional:file:.env[.properties]`. Format: properties style (`key=value`, no `export` prefix).
- `compose.yaml` reads `POSTGRES_*` with fallback to defaults — local devs can skip `.env` entirely.
- Add a new secret like this:
  1. Add the line to `.env.example` with an empty or placeholder value.
  2. Add the real value in your local `.env`.
  3. Share the value with the friend out-of-band (Signal / password manager / verbally).
- CI/prod secrets belong in GitHub Actions Secrets or the hosting provider's equivalent — **not** in a committed `.env` anywhere.
- If a secret ends up in git: rotate it immediately. Removing it in a new commit is not enough — it's still in history.

## What NOT to do

- **Push directly to `main`** — always go through a PR.
- **Mock the database in integration tests** — use Testcontainers. Mock-based tests lie about Postgres-specific behavior.
- **Put business logic in Controllers** — controllers are a transport layer.
- **Expose JPA entities through REST** — always map to a DTO.
- **Change the DB schema without a Flyway migration**.
- **Use `@Data` on entities** (equals/hashCode trouble).
- **Commit `.env`, API keys, passwords or other secrets** — see the section above.

## Domain vocabulary

Use these terms consistently in code, commits, issues and discussion. Detailed model in [DOMAIN.md](DOMAIN.md).

- **User** — The person behind the account. Authentication identity, no gameplay state.
- **Ship** — The player's mothership. In v1 every User has exactly one Ship; the schema permits more for future fleet support.
- **Planet** — A pre-seeded point on the map that a Ship can land on.
- **Tile** — A square on the 100×100 grid, identified by `(x, y)`. Not stored as a table — only interesting things (Ship, Planet) have coordinates.
- **Tick** — A recurring time interval (≤ 1 min) when the world is processed: ships in motion are advanced, future feature effects are triggered. Scheduled centrally.
- **World** — The global state all players share (grid size, current tick). A single `WorldState` singleton row.

Terms that are **not** used (to avoid confusion):
- "turn" — we have ticks, not turns.
- "round" / "match" — this isn't a session-based game world.
- "kingdom" / "empire" — this isn't a kingdom-builder.

## When Claude works in this repo

- Check [README.md](README.md) first if anything in this file is unclear.
- Don't suggest code that breaks the conventions above without flagging it.
- Before suggesting a new dependency: check whether the Spring Boot parent already manages it.
- If a task feels too large for one PR: propose splitting it into multiple issues instead of just charging ahead.

### Keep the docs alive

After every task that changes the project — new feature, new dependency, new architecture decision, new domain term, new convention — **go back and verify** that the following files are still correct:

- `CLAUDE.md` — domain vocabulary, package structure, conventions, anti-patterns
- `DOMAIN.md` — entity schema, deferred features, forward-compat notes
- `README.md` — stack, getting-started commands, project structure

If anything is wrong or incomplete:
1. Flag it to the user with a short description of what no longer holds.
2. Propose a concrete change (ideally as a diff or exact new text).
3. Include the doc change in the **same PR** as the code change — not a follow-up PR. Stale docs that survive a week are enough to confuse someone.

This applies even when the user *didn't* ask for a doc update. Silent documentation drift is one of the biggest reasons CLAUDE.md loses value over time.
