import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { verifyPrerender } from './verify-prerender.mjs';

const HOME = '<section id="hero"></section><section id="projects"></section>';
const PROJECT = '<h1>GameVault</h1>';

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
  const problems = verifyPrerender(dist({ ...complete, 'es/index.html': HOME + '<p class="status">Cargando…</p>' }));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /es[\\/]index\.html/);
});

test('reports a missing locale and a locale without project pages', () => {
  const problems = verifyPrerender(dist({ 'es/index.html': HOME }));
  assert.equal(problems.length, 2);
  assert.ok(problems.some((p) => p.includes('en')));
  assert.ok(problems.some((p) => p.includes('project')));
});
