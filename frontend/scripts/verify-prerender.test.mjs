import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { verifyPrerender, verifySiteUrl } from './verify-prerender.mjs';

test('requires the public SITE_URL when building on Cloudflare Pages', () => {
  assert.equal(verifySiteUrl({ CF_PAGES: '1' }).length, 1);
  assert.equal(verifySiteUrl({ CF_PAGES: '1', SITE_URL: 'http://localhost:8080' }).length, 1);
  assert.deepEqual(verifySiteUrl({ CF_PAGES: '1', SITE_URL: 'https://victor.pages.dev' }), []);
  assert.deepEqual(verifySiteUrl({}), []);
});

// `ngh="…"` is the hydration annotation Angular writes when hydration is set up correctly.
const HOME = '<main ngh="0"><section id="hero"></section><section id="projects"></section></main>';
const PROJECT = '<main ngh="0"><h1>GameVault</h1></main>';

function dist(files) {
  const root = mkdtempSync(join(tmpdir(), 'prerender-'));
  for (const [path, html] of Object.entries(files)) {
    mkdirSync(join(root, path, '..'), { recursive: true });
    writeFileSync(join(root, path), html);
  }
  return root;
}

const complete = {
  'es/index.html': HOME,
  'en/index.html': HOME,
  'es/projects/gamevault/index.html': PROJECT,
  'en/projects/gamevault/index.html': PROJECT,
};

test('passes a complete prerender', () => {
  assert.deepEqual(verifyPrerender(dist(complete)), []);
});

test('reports a page left in the loading or error state', () => {
  const problems = verifyPrerender(dist({ ...complete, 'es/index.html': HOME + '<p data-state="loading">Cargando…</p>' }));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /es[\\/]index\.html/);
});

test('reports a section that failed inside an otherwise complete page', () => {
  const failedSection = HOME.replace('</section></main>', '<div data-state="error">Los proyectos no cargaron.</div></section></main>');
  const problems = verifyPrerender(dist({ ...complete, 'en/index.html': failedSection }));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /en[\\/]index\.html/);
});

test('reports pages without hydration annotations', () => {
  const problems = verifyPrerender(dist({ ...complete, 'es/projects/gamevault/index.html': '<h1>GameVault</h1>' }));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /hydration/);
});

test('reports a missing locale and a locale without project pages', () => {
  const problems = verifyPrerender(dist({ 'es/index.html': HOME }));
  assert.equal(problems.length, 2);
  assert.ok(problems.some((p) => p.includes('en')));
  assert.ok(problems.some((p) => p.includes('project')));
});
