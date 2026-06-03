// `defineConfig` comes from `vitest/config` (not `vite`) so the same file
// configures both `vite build` and `vitest run`. Vitest's defineConfig is a
// superset — it adds the `test` field and otherwise behaves identically to
// Vite's. No second config file needed.
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
    server: {
        // Dev-only proxy: any request the SPA makes to /api/* is forwarded to the
        // Spring Boot backend on :8080. The browser sees same-origin (5173), so:
        //   - no CORS preflight needed
        //   - session cookies set by the backend ride along automatically
        //   - frontend code calls `/api/...` (relative) and works identically in
        //     dev and in prod (where the same paths will be same-origin too, via
        //     reverse proxy or Spring serving the built frontend).
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./src/test/setup.ts'],
        // Keep tests fast: don't sweep node_modules, dist, build output.
        include: ['src/**/*.{test,spec}.{ts,tsx}'],
        css: false,
    },
})
