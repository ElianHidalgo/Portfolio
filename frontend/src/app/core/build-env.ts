export interface BuildEnv {
  /** Where the prerenderer reads the public API. */
  apiUrl: string;
  /** Public origin of the site, for canonical and hreflang links. */
  siteUrl: string;
  /** `PRERENDER=false` builds a client-only app (local Docker, where no API exists at build time). */
  prerender: boolean;
}

const withoutTrailingSlash = (url: string) => url.replace(/\/+$/, '');

/** Reads build-time settings. None is secret; they never reach the browser bundle. */
export function readBuildEnv(env: Record<string, string | undefined>): BuildEnv {
  return {
    apiUrl: withoutTrailingSlash(env['API_URL'] || 'http://localhost:8080'),
    siteUrl: withoutTrailingSlash(env['SITE_URL'] || 'http://localhost:8080'),
    prerender: env['PRERENDER'] !== 'false',
  };
}

/** Settings of the current build (prerender runs in Node). */
export const buildEnv = readBuildEnv(process.env);
