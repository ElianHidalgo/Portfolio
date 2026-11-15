import { RenderMode, ServerRoute } from '@angular/ssr';
import { buildEnv } from './core/build-env';
import { fetchWithRetry } from './core/http/fetch-with-retry';

/** Slugs of every published project, read from the API at build time. */
async function projectSlugs(): Promise<Array<{ slug: string }>> {
  // The first build request may wake the sleeping backend; later ones find it awake.
  const url = `${buildEnv.apiUrl}/api/projects`;
  const response = await fetchWithRetry(url);
  if (!response.ok) {
    throw new Error(`Prerender: ${url} answered ${response.status}`);
  }
  const projects = (await response.json()) as Array<{ slug: string }>;
  return projects.map(({ slug }) => ({ slug }));
}

const prerendered: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Prerender },
  { path: 'projects/:slug', renderMode: RenderMode.Prerender, getPrerenderParams: projectSlugs },
  // Anything else (unknown slugs, the future admin area) renders in the browser.
  { path: '**', renderMode: RenderMode.Client },
];

export const serverRoutes: ServerRoute[] = buildEnv.prerender
  ? prerendered
  : [{ path: '**', renderMode: RenderMode.Client }];
