# Frontend

React + Vite + TypeScript SPA. Talks to the Spring Boot backend on `:8080`.

## Setup

We use **pnpm**, not npm. (npm's lockfile is platform-specific and the project mixes Windows and Linux contributors — pnpm's lockfile is cross-platform, see `CLAUDE.md` "Quality bar".)

One-time on a new machine:

```sh
# enable Corepack (ships with Node 16+) — picks up the pnpm version pinned
# in package.json's `packageManager` field automatically. Needs admin on
# Windows when Node was installed system-wide. If that fails, install pnpm
# directly from https://pnpm.io/installation (user-dir install, no admin).
corepack enable
```

Then in this directory:

```sh
pnpm install
pnpm run dev
```

App is then on http://localhost:5173. Backend should be running on `:8080` (`./mvnw spring-boot:run` from the repo root). The Vite dev server proxies `/api/*` to the backend — your code calls `fetch('/api/...')` directly, no need for absolute URLs.

## Scripts

| Script             | What it does                             |
| ------------------ | ---------------------------------------- |
| `pnpm run dev`     | Vite dev server with hot module reload   |
| `pnpm run build`   | Type-check + production build to `dist/` |
| `pnpm run lint`    | ESLint                                   |
| `pnpm run format`  | Prettier — format everything in place    |
| `pnpm run preview` | Serve the production build locally       |

## Notes

- No backend address in the code or env vars: paths like `/api/health` are relative. In dev, Vite's proxy (`vite.config.ts`) forwards them to `:8080`. In prod, the SPA is expected to be served same-origin with the backend (reverse proxy or Spring serving the built `dist/`).
- See [CLAUDE.md](../CLAUDE.md) ("Frontend" section) for the project-wide React + Pixi conventions.
