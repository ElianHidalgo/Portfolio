import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { verifyBuild, verifyPrerender, verifySiteUrl } from './verify-prerender.mjs';

test('checks SITE_URL on Cloudflare even when prerendering is off', () => {
  const problems = verifyBuild({ WORKERS_CI: '1', PRERENDER: 'false' }, '/does-not-matter');
  assert.equal(problems.length, 1);
  assert.match(problems[0], /SITE_URL/);
});

test('requires the public SITE_URL when building on Cloudflare (Pages or Workers)', () => {
  assert.equal(verifySiteUrl({ CF_PAGES: '1' }).length, 1);
  assert.equal(verifySiteUrl({ CF_PAGES: '1', SITE_URL: 'http://localhost:8080' }).length, 1);
  assert.deepEqual(verifySiteUrl({ CF_PAGES: '1', SITE_URL: 'https://victor.pages.dev' }), []);
  assert.equal(verifySiteUrl({ WORKERS_CI: '1' }).length, 1);
  assert.deepEqual(verifySiteUrl({ WORKERS_CI: '1', SITE_URL: 'https://victor.workers.dev' }), []);
  assert.deepEqual(verifySiteUrl({}), []);
});

// `ngh="…"` is the hydration annotation Angular writes when hydration is set up correctly.
const HOME = '<meta name="api-origin" content="https://api.example">'
  + '<main ngh="0"><section id="hero"></section><section id="projects"></section><section id="contact"></section></main>';
const PROJECT = '<main ngh="0"><h1>GameVault</h1></main>';

function dist(files) {
  const root = mkdtempSync(join(tmpdir(), 'prerender-'));
  for (const [path, html] of Object.entries(files)) {
    mkdirSync(join(root, path, '..'), { recursive: true });
    writeFileSync(join(root, path), html);
  }
  return root;
}

const ADMIN_SHELL = '<head><meta name="api-origin" content="https://api.example"></head>';

const complete = {
  'es/admin/index.html': ADMIN_SHELL,
  'es/admin/login/index.html': ADMIN_SHELL,
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
  const failedSection = HOME.replace('</section><section id="contact">', '<div data-state="error">Los proyectos no cargaron.</div></section><section id="contact">');
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
  const problems = verifyPrerender(dist({ 'es/index.html': HOME, 'es/admin/index.html': ADMIN_SHELL, 'es/admin/login/index.html': ADMIN_SHELL }));
  assert.equal(problems.length, 2);
  assert.ok(problems.some((p) => p.includes('en')));
  assert.ok(problems.some((p) => p.includes('project')));
});

test('reports a home page without the contact section or the API origin', () => {
  const home = '<main ngh="0"><section id="hero"></section><section id="projects"></section></main>';
  const problems = verifyPrerender(dist({ ...complete, 'es/index.html': home }));
  assert.equal(problems.length, 2);
  assert.ok(problems.every((p) => /es[\\/]index\.html/.test(p)));
});

test('reports missing admin shells', () => {
  const { 'es/admin/login/index.html': _missing, ...withoutLogin } = complete;
  const problems = verifyPrerender(dist({ ...withoutLogin, 'es/admin/index.html': '<head></head>' }));
  assert.equal(problems.length, 2);
  assert.ok(problems.some((p) => /admin[\\/]login/.test(p)));
  assert.ok(problems.some((p) => /api-origin/.test(p)));
});
