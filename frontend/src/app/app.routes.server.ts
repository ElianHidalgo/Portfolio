import { RenderMode, ServerRoute } from '@angular/ssr';
import { buildEnv } from './core/build-env';

/** Slugs of every published project, read from the API at build time. */
async function projectSlugs(): Promise<Array<{ slug: string }>> {
  const url = `${buildEnv.apiUrl}/api/projects`;
  let response: Response;
  try {
    response = await fetch(url);
  } catch {
    throw new Error(`Prerender: cannot reach ${url}`);
  }
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
