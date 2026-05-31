# Frontend

React + Vite + TypeScript SPA. Talks to the Spring Boot backend on `:8080`.

## Setup

```sh
npm install
npm run dev
```

App is then on http://localhost:5173. Backend should be running on `:8080` (`./mvnw spring-boot:run` from the repo root). The Vite dev server proxies `/api/*` to the backend — your code calls `fetch('/api/...')` directly, no need for absolute URLs.

## Scripts

| Script            | What it does                             |
| ----------------- | ---------------------------------------- |
| `npm run dev`     | Vite dev server with hot module reload   |
| `npm run build`   | Type-check + production build to `dist/` |
| `npm run lint`    | ESLint                                   |
| `npm run format`  | Prettier — format everything in place    |
| `npm run preview` | Serve the production build locally       |

## Notes

- No backend address in the code or env vars: paths like `/api/health` are relative. In dev, Vite's proxy (`vite.config.ts`) forwards them to `:8080`. In prod, the SPA is expected to be served same-origin with the backend (reverse proxy or Spring serving the built `dist/`).
- See [CLAUDE.md](../CLAUDE.md) ("Frontend" section) for the project-wide React + Pixi conventions.
