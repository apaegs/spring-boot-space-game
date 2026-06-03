// Vitest setup: pulled in by every test file via `setupFiles` in vite.config.ts.
//
// - `@testing-library/jest-dom` registers the `toBeInTheDocument`, `toHaveClass`
//   etc. matchers on `expect`. Vitest's `expect` extends from this import.
// - The afterEach cleanup is auto-registered by @testing-library/react when
//   `globals: true` is set in the test config (which we do), so we don't need
//   to call `cleanup()` manually.
import '@testing-library/jest-dom/vitest'
