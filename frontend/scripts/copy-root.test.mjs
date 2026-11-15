import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { writeRootFiles } from './copy-root.mjs';

function build(env = {}) {
  const out = mkdtempSync(join(tmpdir(), 'root-'));
  writeRootFiles(out, env);
  return (name) => readFileSync(join(out, name), 'utf8');
}

test('writes the language page with the tested picker and the public URL', () => {
  const read = build({ SITE_URL: 'https://site.pages.dev/' });
  assert.match(read('index.html'), /function pickLocale/);
  assert.match(read('index.html'), /href="https:\/\/site\.pages\.dev\/en\/"/);
});

test('sends unknown paths of each language to its client-only shell (static host)', () => {
  const redirects = read => read('_redirects');
  const text = redirects(build());
  assert.match(text, /^\/es\/\* \/es\/index\.csr\.html 200$/m);
  assert.match(text, /^\/en\/\* \/en\/index\.csr\.html 200$/m);
});

test('builds a CSP that allows exactly the inline scripts of the built pages', () => {
  const out = mkdtempSync(join(tmpdir(), 'root-'));
  mkdirSync(join(out, 'es'));
  writeFileSync(join(out, 'es', 'index.html'),
    '<script>boot()</script><script id="ng-state" type="application/json">{"a":1}</script><script src="main.js"></script>');
  writeRootFiles(out, {});
  const headers = readFileSync(join(out, '_headers'), 'utf8');
  const hash = (code) => `'sha256-${createHash('sha256').update(code).digest('base64')}'`;

  assert.ok(headers.includes(hash('boot()')), 'inline script hash missing');
  assert.ok(!headers.includes(hash('{"a":1}')), 'JSON data must not be whitelisted as script');
  assert.match(headers, /Content-Security-Policy: default-src 'self'; script-src 'self' 'sha256-/);
  assert.match(headers, /object-src 'none'/);
  assert.match(headers, /frame-ancestors 'none'/);
  assert.doesNotMatch(headers, /script-src[^;]*'unsafe-inline'/);
});

test('sets security headers and long caching for hashed assets', () => {
  const headers = build()('_headers');
  for (const header of [
    'Strict-Transport-Security: max-age=31536000; includeSubDomains',
    'X-Content-Type-Options: nosniff',
    'Referrer-Policy: strict-origin-when-cross-origin',
    'X-Frame-Options: DENY',
    'Permissions-Policy: camera=(), microphone=(), geolocation=()',
  ]) {
    assert.ok(headers.includes(header), `missing ${header}`);
  }
  assert.match(headers, /Cache-Control: public, max-age=31536000, immutable/);
});
