// Fails the build when the prerendered site is incomplete: a missing language, no project
// pages, a page or section frozen in its loading/error state (e.g. the API answered badly
// mid-build), or pages the browser could not hydrate.
// Publishing nothing is better than publishing a broken page.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const LOCALES = ['es', 'en'];
// Every loading/error/not-found state in the app carries data-state="…" (whole pages and sections).
const STATE_MARKER = 'data-state="';
// Angular writes ngh="…" on hydratable components; without it the browser redraws the page.
const HYDRATION_MARKER = ' ngh="';

/**
 * On Cloudflare builds (Pages sets CF_PAGES, Workers Builds sets WORKERS_CI), SITE_URL must be the public URL:
 * otherwise every canonical/hreflang/og link would be published pointing at localhost.
 */
export function verifySiteUrl(env) {
  if (!env.CF_PAGES && !env.WORKERS_CI) return [];
  const siteUrl = env.SITE_URL ?? '';
  return !siteUrl || /localhost|127\.0\.0\.1/.test(siteUrl)
    ? [`SITE_URL must be the public URL of the site (got "${siteUrl}")`]
    : [];
}

/** Returns a list of problems; empty means the prerender is complete. */
export function verifyPrerender(distDir) {
  const problems = [];
  const check = (file, required) => {
    const html = readFileSync(join(distDir, file), 'utf8');
    for (const marker of required) {
      if (!html.includes(marker)) problems.push(`${file}: missing ${marker}`);
    }
    if (html.includes(STATE_MARKER)) problems.push(`${file}: rendered a loading or error state`);
    if (!html.includes(HYDRATION_MARKER)) problems.push(`${file}: has no hydration annotations`);
  };

  for (const locale of LOCALES) {
    const home = join(locale, 'index.html');
    if (!existsSync(join(distDir, home))) {
      problems.push(`${home} is missing (locale "${locale}" was not built)`);
      continue;
    }
    // The contact form needs its section and the API origin it posts to.
    check(home, ['id="hero"', 'id="projects"', 'id="contact"', 'name="api-origin"']);

    const projectsDir = join(distDir, locale, 'projects');
    const slugs = existsSync(projectsDir) ? readdirSync(projectsDir) : [];
    if (slugs.length === 0) problems.push(`${locale}: no project pages were prerendered`);
    for (const slug of slugs) check(join(locale, 'projects', slug, 'index.html'), ['<h1']);
  }
  return problems;
}

/** All build checks: SITE_URL always (the root page uses it), prerender output when enabled. */
export function verifyBuild(env, distDir) {
  const prerendered = env.PRERENDER !== 'false';
  return [...verifySiteUrl(env), ...(prerendered ? verifyPrerender(distDir) : [])];
}

// CLI: node scripts/verify-prerender.mjs
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const problems = verifyBuild(process.env, fileURLToPath(new URL('../dist/frontend/browser', import.meta.url)));
  if (problems.length > 0) {
    console.error(`Build verification failed:\n- ${problems.join('\n- ')}`);
    process.exit(1);
  }
  console.log(process.env.PRERENDER === 'false' ? 'Prerender disabled — SITE_URL checked.' : 'Prerender verified.');
}
