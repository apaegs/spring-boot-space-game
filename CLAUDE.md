# CLAUDE.md

This file is for two readers: **the friend being onboarded** and **Claude the agent**. Both should be able to read it and start working in the project without asking.

## What the project is

A 2D, tick-based space game in the browser — closest comparison is a web-based relative of Elite. Each player controls a mothership on a fixed 100×100 grid with planets and stars. The player sets a destination, the ship moves 1 tile per tick, and on arrival at a planet the player can land and interact. The world ticks in the background (≤ 1 min) regardless of whether anyone is logged in — it's not real-time strategy, more "log in now and then and make decisions."

Detailed domain model: see [DOMAIN.md](DOMAIN.md).

## Quality bar

This is a real game we want to ship and be proud of, not a weekend hack. When you face a choice between "the proper fix" and "the quick workaround":

- **Prefer the proper fix.** If `npm ci` complains about a lockfile mismatch, fix the actual platform-deps problem — don't switch to `npm install` to silence it. If a test is flaky, find the race — don't add a retry. If a CodeRabbit finding looks low-value, skip it only when the fix genuinely costs more than the value, never because "the project is small".
- **No "it's just a hobby project" excuses.** That mindset compounds. Every shortcut taken today narrows what's possible tomorrow.
- **Velocity is fine. Sloppy is not.** Move fast when the change is small and understood. Slow down when it isn't. They aren't opposites.

When unsure whether a fix is proper enough: surface the trade-off and ask. "Here are three approaches, which fits?" beats "I picked the quick one because hobby". The right answer is sometimes still the quick one — but it should be a deliberate choice, not a default.

This applies retroactively too. If you spot an old shortcut in the codebase, flag it as a follow-up — don't leave it because "it was the standard at the time".

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
- **TS API types mirror the backend records.** `frontend/src/types/api.ts` is hand-written, not generated. The file's top comment lists every type's backing Java source-of-truth — update both sides in the same PR when a DTO changes.
- **"Bodies" not "planets".** The map renders `CelestialBody` (planets, asteroids, gas giants, stars). TS types, selection discriminator (`{ kind: 'body' }`), API path (`/api/bodies`), and Pixi layer names all use "body" / "bodies". User-facing labels can still say "planet" where it's accurate (e.g. for `ROCKY_PLANET` kind in the panel).
- **PixiJS** for the 2D map — a `<canvas>` mounted in a React component. Pixi code lives isolated from the React tree (Pixi owns its own render loop); React only syncs state in via props/refs and reads events back via callbacks.
- **Not** Phaser — overkill for tick-based.
- Animated numbers via a lightweight lib (e.g. `framer-motion` or `react-spring`); no reason to write your own easing functions.
- State: start with React state + `@tanstack/react-query` for server state. Don't reach for Redux/Zustand before it's actually needed.

## Code conventions

### Package structure
Package **by domain**, not by technical layer:
```text
org.example.springbootspacegame
├── ship/           # Entity, Repository, Service, Controller for Ship (+ ShipType, ShipCargo)
├── body/           # CelestialBody, BodyResource, BodyBuyPrice + repos/service
├── resource/       # ResourceKind enum (catalog of extractable/tradeable resources)
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

### DTOs
Use Java **records**, not Lombok-annotated classes. Records give immutability, `equals`/`hashCode`/`toString` for free, and signal "this is a value type, not an entity" at a glance. Convert from JPA entities with a static factory on the record, e.g. `ShipDto.from(ship)`.

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
- Link the issue: `Closes #N` (or `Closes #A, closes #B` when the PR resolves several — each needs its own `closes` keyword).
- Description should say *why*, not just *what* (the diff shows what).
- **Prefer bigger, cohesive PRs over many small ones.** This is a single-developer workflow with a real per-PR overhead (CodeRabbit rate-limits + credit usage, manual review loop, branch churn). A focused PR that closes 2–3 related issues with the same theme — same subsystem, same code paths, same test surface, doc updates piggybacked — is the default. Soft ceiling around 600–800 lines; over that, even a cohesive narrative is hard to review.
- A bundle is **not** right when themes diverge (frontend animation + backend index migration) or when a tiny fix gets dragged along by a substantial feature just because both happen to be open. State the bundling decision explicitly in the PR body ("Bundled because they share …").
- One review required. CI must be green.
- Squash and merge.

### Issues
- Write issues in **English** — matches the rest of the project (code, commits, PRs, docs). Earlier issues (#2–#17) are in Swedish; new ones aren't.
- `.github/ISSUE_TEMPLATE/` has two templates: **Bug** (what happened, expected, repro) and **Feature** (Goal / Scope / Acceptance — same structure the earlier issues used, just translated).
- Pick the matching template when filing. Blank issues still work but the template is the default path.

## Auth and CSRF

- **Session-based auth.** A successful `POST /api/auth/login` returns 204 and sets `JSESSIONID`. Every subsequent request rides that cookie. `POST /api/auth/logout` invalidates it. No JWTs — we deliberately stayed stateful for v1 simplicity.
- **CSRF protection is on.** Spring Security's SPA pattern: server writes a non-HttpOnly `XSRF-TOKEN` cookie; the frontend reads it and echoes the value as the `X-XSRF-TOKEN` header on every state-changing request (POST/PUT/PATCH/DELETE). The cookie is force-issued on every response via a small `CsrfCookieFilter` so the SPA always has a token to send.
- **Exempt endpoints.** `/api/auth/register` and `/api/auth/login` are CSRF-exempt (`ignoringRequestMatchers`). They're unauthenticated by design — there's no session for an attacker to ride, so the protection is moot, and requiring a token would force a "fetch token first" round-trip into login. GET endpoints are exempt by HTTP method (Spring's default).
- **Frontend.** `frontend/src/api/client.ts` reads `XSRF-TOKEN` and sets `X-XSRF-TOKEN` automatically — endpoint modules don't have to think about it.
- **Tests.** Integration tests that POST/PUT/PATCH/DELETE must chain `.with(csrf())` on the MockMvc builder (import from `SecurityMockMvcRequestPostProcessors`). The convention in this repo is to add it on every `.session(...)` call so the test reads like a real authenticated client (it's a no-op on GETs). Register/login calls don't need it — they're exempt.

## Observability

- **Error responses follow a stable JSON shape.** Every non-2xx response from the API matches `ApiErrorResponse`: `{ "status": <int>, "message": <string>, "details"?: { field: msg }, "errorId"?: <uuid> }`. The frontend's `ApiError` in `frontend/src/api/client.ts` is the source of truth on the client side and binds to this shape. `details` appears only on validation 400s; `errorId` only on internal 500s (so a player can quote it in a bug report and we grep the logs straight to their request).
- **All exception → response mapping lives in `errors/GlobalExceptionHandler`** (`@RestControllerAdvice`). For paths that fail inside the Spring Security filter chain (unauthenticated, CSRF rejected), `errors/JsonSecurityErrorHandlers` writes the same JSON shape via custom `AuthenticationEntryPoint` and `AccessDeniedHandler`. Don't write per-controller try/catch for error shaping — surface the failure as a `ResponseStatusException` (or a more specific exception that the handler covers) and let the advice do its job.
- **Logging is profile-aware.** `src/main/resources/logback-spring.xml` switches encoders by Spring profile: the default profile emits human-readable colored console output (unchanged dev feel); the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) emits one JSON event per line via `logstash-logback-encoder` with `@timestamp`, `level`, `logger_name`, `thread_name`, `message`, `mdc`, and `stack_trace` on errors. A log shipper (Loki / CloudWatch / etc.) can index the prod stream by field without a parser ruleset.
- **MDC and request-id correlation.** `observability/MdcFilter` (wired into the Spring Security chain right after `SecurityContextHolderFilter`) populates the MDC with `requestId` (UUID, per request) and `userId` (the authenticated user's UUID, or `"anonymous"`). The same UUID goes out as the `X-Request-Id` response header on every response — including 401/403, so a player can quote it from any failure. Don't `MDC.put()` these manually; the filter handles it. Adding new MDC keys is fine, but clear them in `finally` to avoid thread-pool leakage.

## Secrets and environment variables

- **Anything sensitive goes in `.env`** (gitignored). Template: `.env.example` (committed, no real values).
- Spring reads `.env` automatically via `spring.config.import=optional:file:.env[.properties]`. Format: properties style (`key=value`, no `export` prefix).
- `compose.yaml` has **no default credentials** — `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are all required from `.env`. This is deliberate: a committed default is a committed secret, even if it's "just a dev value". Every dev picks their own password locally.
- The dev Postgres binds to `127.0.0.1:5432`, not `0.0.0.0`. Don't change this without a reason — it keeps the container off the LAN regardless of password strength.
- Add a new secret like this:
  1. Add the line to `.env.example` with an empty or placeholder value.
  2. Add the real value in your local `.env`.
  3. Share the value with the friend out-of-band (Signal / password manager / verbally).
- CI/prod secrets belong in GitHub Actions Secrets or the hosting provider's equivalent — **not** in a committed `.env` anywhere.
- If a secret ends up in git: rotate it immediately. Removing it in a new commit is not enough — it's still in history. For `POSTGRES_PASSWORD` specifically: `docker compose down -v` to drop the local volume, then pick a new password in `.env`. Postgres can't re-key an existing user from environment variables after first init.

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

- **User** — The person behind the account. Authentication identity + `credits` (in-game currency).
- **Ship** — The player's mothership. Every Ship has a `ship_type_id` pointing into the ship types catalog (v1: only `MOTHERSHIP`).
- **ShipType** — A row in the ship types catalog. Stats per type (cargo capacity, extract rate) live here, not on the ship row.
- **ShipCargo** — Per-(ship, resource) inventory row. Cargo cap is enforced against the sum across all resources.
- **CelestialBody** — A pre-seeded point on the map (planet, asteroid, gas giant, star). Has a `kind` from `CelestialBodyKind`. Bodies are the only valid LAND targets and the only source of resources.
- **BodyResource** — Per-(body, resource) reserve. The EXTRACT handler decrements it.
- **BodyBuyPrice** — Per-(body, resource) buy price. The SELL handler reads it.
- **ResourceKind** — The catalogue of resources (`IRON`, `WATER`, `HYDROGEN`, `HELIUM`, `RARE_METAL`). Each declares its required extraction state.
- **Tile** — A square on the 100×100 grid, identified by `(x, y)`. Not stored as a table — only interesting things (Ship, CelestialBody) have coordinates.
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
