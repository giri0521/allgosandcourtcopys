import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@/app/App';
import { api } from '@/lib/api';
import { fixtureAdapter } from '@/preview/fixtures';
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
 * The app uses a browser router, so it reads the path — which on this page is
 * `/screens-preview.html` and matches nothing. The route to render is passed in the hash instead
 * (`/screens-preview.html#/admin/reports`) and rewritten into the path before the router mounts.
 *
 * <p>The hash rather than the path because a real path would be served the *application's*
 * index.html by the dev server, not this harness.
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
