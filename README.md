# spring-boot-space-game

A 2D, tick-based space game in the browser — closest comparison is a web-based relative of Elite. Each player controls a mothership on a 100×100 grid with planets and stars. The world ticks in the background, ships move toward their destinations, and the player logs in now and then to make decisions.

Detailed domain model: see [DOMAIN.md](DOMAIN.md).

## Stack

- **Backend**: Spring Boot 4 (Java 25), Spring MVC, Spring Security, Spring Data JPA
- **Database**: PostgreSQL 16 + Flyway migrations
- **Frontend**: React + Vite (SPA) with PixiJS for the 2D map (scaffolded in [issue #4](../../issues))
- **Tests**: JUnit 5 + Testcontainers (real Postgres, no mocks)
- **CI**: GitHub Actions

## Getting started locally

Requirements: JDK 25, Docker (for Postgres), Git.

```sh
# 1. Clone and enter
git clone <repo-url>
cd spring-boot-space-game

# 2. Create your local .env (required — compose.yaml has no defaults)
cp .env.example .env
# Open .env and set POSTGRES_PASSWORD to a real value, e.g.
#   openssl rand -base64 24

# 3. Start the backend — Spring detects compose.yaml and starts Postgres automatically
./mvnw spring-boot:run

# 4. Run tests (uses Testcontainers, ignores .env / compose.yaml)
./mvnw verify
```

The app listens on http://localhost:8080.

If you prefer to start Postgres separately:
```sh
docker compose up -d
```

### Environment variables / .env

`.env` is **required** — `compose.yaml` deliberately has no default
credentials, so the backend won't start until you create one.

```sh
cp .env.example .env
```

Then edit `.env` and set at minimum:

- `POSTGRES_DB` — any name (e.g. `spacegame`)
- `POSTGRES_USER` — any name (e.g. `spacegame`)
- `POSTGRES_PASSWORD` — **must be a real value**, generated locally. Don't
  reuse a value from chat / docs / another machine. Example:
  `openssl rand -base64 24`.

Other notes:

- `.env` is **gitignored** — never commit it.
- The dev Postgres binds to `127.0.0.1:5432`, not `0.0.0.0`, so the container
  isn't reachable from your LAN.
- Spring loads `.env` via `spring.config.import` in `application.properties`.
  Format is regular properties (`key=value`).
- For future API keys: add the line to `.env.example` (without the value)
  **and** to your local `.env` (with the value).

**Never share secrets via git, issues, PRs or chat history.** Use Signal, a
password manager, or say it verbally. If a secret leaks: rotate it
immediately, don't just add a commit that "removes it."

#### Rotating a leaked password

If a `POSTGRES_PASSWORD` ever ends up somewhere it shouldn't (git, chat,
screenshot), rotate it:

```sh
# Drop the local database volume — it stores the user with the old password,
# and Postgres can't re-key it after first init.
docker compose down -v

# Set a new password in .env, then start fresh:
./mvnw spring-boot:run
```

Flyway re-runs all migrations on the empty volume — local data is lost,
which is fine for dev.

## Workflow

We run a strict PR flow — **no direct pushes to `main`**.

1. Find or create an **issue** describing what's to be done.
2. Create a branch: `feature/<short-description>` or `fix/<short-description>`.
3. Commit small, focused changes.
4. Open a **pull request** against `main`. Link the issue (`Closes #N`).
5. The other person **reviews** and approves.
6. CI must be green before merge.
7. Merge via "Squash and merge".

More detail on conventions in [CLAUDE.md](CLAUDE.md).

## Project structure

```text
.
├── src/main/java/org/example/springbootspacegame/   # Backend code, packaged by domain
├── src/main/resources/
│   ├── application.properties                       # Configuration
│   ├── db/migration/                                # Flyway SQL migrations (V1__...)
│   └── static/                                      # (frontend build lands here later)
├── src/test/java/                                   # Tests
├── compose.yaml                                     # Local Postgres
├── pom.xml
└── .github/workflows/ci.yml                         # CI
```

The frontend lives in `frontend/` (React + Vite, scaffolded in [issue #4](../../issues)). 2D rendering is done via PixiJS in a `<canvas>` inside the React app; plain DOM React for menus, dashboards and forms.
