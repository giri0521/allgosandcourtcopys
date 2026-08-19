import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@/app/App';
import { api } from '@/lib/api';
import { fixtureAdapter } from '@/preview/fixtures';
import { THEME_STORAGE_KEY } from '@/lib/theme';
import '@/index.css';

/**
 * The whole application, driven by fixtures instead of a backend.
 *
 * <p>This renders the <b>real</b> `App` — the real router, the real `AuthProvider`, the real
 * screens. Only the HTTP layer is swapped, so what appears on screen is what a user would see. The
 * start-up `/auth/refresh` is answered with an admin session, which is what lets the guarded routes
 * render at all.
 *
 * <p>It exists because this project's recurring failure is a green build over a broken screen: three
 * separate phases shipped code that compiled, linted and had passing tests while a screen was
 * unreachable or unrendered. A type checker cannot see a layout, and an integration test cannot see
 * an empty state. This can.
 *
 * <p>It is not a substitute for running against the real API — it proves nothing about wiring,
 * permissions or query correctness. It proves the screens draw.
 *
 * <p>Open `/screens-preview.html#/admin/reports` on the dev server, or drive it with Playwright.
 * Vite only builds index.html, so none of this reaches production.
 */
api.defaults.adapter = fixtureAdapter;

/**
 * `?theme=dark` on the URL, so both appearances can be screenshotted without clicking anything.
 *
 * <p>Written to the same storage key the application uses, before the first render, which means the
 * preview goes through the real provider rather than a special path — if the theme were forced some
 * other way here, this harness would stop being evidence about the real thing.
 */
const requestedTheme = new URLSearchParams(window.location.search).get('theme');
if (requestedTheme === 'dark' || requestedTheme === 'light' || requestedTheme === 'system') {
  window.localStorage.setItem(THEME_STORAGE_KEY, requestedTheme);
}

/**
 * The app uses a browser router, so it reads the path — which on this page is
 * `/screens-preview.html` and matches nothing. The route to render is passed in the hash instead
 * (`/screens-preview.html#/admin/reports`) and rewritten into the path before the router mounts.
 *
 * <p>The hash rather than the path because a real path would be served the *application's*
 * index.html by the dev server, not this harness.
 *
 * <p>This has to stay below the theme block above: `replaceState` swaps the entire URL for the
 * route and takes the query string with it, so anything reading `?theme=` must already have its
 * copy by the time this runs.
 */
const requested = window.location.hash.replace(/^#/, '');
if (requested.startsWith('/')) {
  window.history.replaceState(null, '', requested);
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
