import React from 'react';
import ReactDOM from 'react-dom/client';
/*
 * Inter, self-hosted rather than pulled from Google Fonts: a government network may sit behind a
 * proxy that blocks external font CDNs, and a typeface that silently fails to load takes the whole
 * visual identity with it.
 *
 * Imported here, from the JavaScript entry, rather than with @import in index.css. Tailwind's
 * PostCSS plugin resolves @import itself and inlines the font's stylesheet *before* Vite sees it,
 * which leaves the `url(./files/…)` references pointing at nothing — the @font-face rules ship, the
 * files do not, and the page quietly falls back to the system font. Going through the module graph
 * makes Vite rewrite the URLs and emit the .woff2 files.
 *
 * The package declares one @font-face per subset with a unicode-range, so a browser downloads only
 * the latin cut unless the text needs more.
 */
import '@fontsource-variable/inter';
import { App } from './app/App';
import './index.css';

/*
 * The QueryClient lives in App, alongside the router and the auth provider, so that the preview
 * harnesses can mount the whole application without duplicating its setup. This file only puts it
 * on the page.
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
