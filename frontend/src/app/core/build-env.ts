/**
 * Build-time settings, read from environment variables while prerendering (Node only).
 * None of them is secret; they never reach the browser bundle.
 */
export const buildEnv = {
  /** Where the prerenderer reads the public API. */
  apiUrl: process.env['API_URL'] || 'http://localhost:8080',
  /** Public origin of the site, for canonical and hreflang links. */
  siteUrl: (process.env['SITE_URL'] || 'http://localhost:8080').replace(/\/+$/, ''),
  /** `PRERENDER=false` builds a client-only app (local Docker, where no API exists at build time). */
  prerender: process.env['PRERENDER'] !== 'false',
};
