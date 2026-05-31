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

# 2. Start the backend — Spring detects compose.yaml and starts Postgres automatically
./mvnw spring-boot:run

# 3. Run tests
./mvnw verify
```

The app listens on http://localhost:8080.

If you prefer to start Postgres separately:
```sh
docker compose up -d
```

### Environment variables / .env

Copy the template and fill in values if you need to override defaults:
```sh
cp .env.example .env
```

- `.env` is **gitignored** — never commit it.
- `compose.yaml` reads `POSTGRES_*` with fallback to defaults, so you can skip `.env` for local development.
- Spring loads `.env` via `spring.config.import` if the file exists. The format is regular properties (`key=value`).
- For future API keys: add the line to `.env.example` (without the value) **and** to `.env` (with the value).

**Never share secrets via git, issues, PRs or chat history.** Use Signal, a password manager, or say it verbally. If a secret leaks: rotate it immediately, don't just add a commit that "removes it."

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

```
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
