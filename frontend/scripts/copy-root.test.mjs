import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
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

test('unknown paths of each language get its client-only shell as the 404 page', () => {
  const out = mkdtempSync(join(tmpdir(), 'root-'));
  for (const locale of ['es', 'en']) {
    mkdirSync(join(out, locale));
    writeFileSync(join(out, locale, 'index.csr.html'), `<html lang="${locale}">shell</html>`);
  }
  writeRootFiles(out, {});

  assert.equal(readFileSync(join(out, 'es', '404.html'), 'utf8'), '<html lang="es">shell</html>');
  assert.equal(readFileSync(join(out, 'en', '404.html'), 'utf8'), '<html lang="en">shell</html>');
  assert.match(readFileSync(join(out, '404.html'), 'utf8'), /function pickLocale/);
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

test('caches fonts per locale and keeps client-only shells out of search engines', () => {
  const headers = build()('_headers');
  for (const rule of ['/es/media/*', '/en/media/*']) {
    assert.match(headers, new RegExp(`^${rule.replace(/[*/]/g, '\\$&')}\\n  Cache-Control: public, max-age=31536000, immutable`, 'm'));
  }
  for (const rule of ['/es/index.csr*', '/en/index.csr*', '/es/404*', '/en/404*', '/404*']) {
    assert.match(headers, new RegExp(`^${rule.replace(/[*/.]/g, '\\$&')}\\n  X-Robots-Tag: noindex`, 'm'));
  }
});

test('lets the browser call the API origin, and nothing else, from the pages', () => {
  assert.match(build({ API_URL: 'https://api.example/' })('_headers'), /connect-src 'self' https:\/\/api\.example;/);
  assert.match(build()('_headers'), /connect-src 'self' http:\/\/localhost:8080;/);
});

test('writes admin shells with the API origin and keeps them out of search results', () => {
  const out = mkdtempSync(join(tmpdir(), 'root-'));
  for (const locale of ['es', 'en']) {
    mkdirSync(join(out, locale));
    writeFileSync(join(out, locale, 'index.csr.html'), `<html lang="${locale}"><head><title>x</title></head><body></body></html>`);
  }
  writeRootFiles(out, { API_URL: 'https://api.example/' });

  for (const shell of ['es/admin/index.html', 'es/admin/login/index.html', 'es/admin/mensajes/index.html', 'es/admin/perfil/index.html', 'es/admin/skills/index.html', 'es/admin/proyectos/index.html', 'es/admin/proyectos/editar/index.html', 'es/404.html', 'en/404.html']) {
    assert.match(readFileSync(join(out, shell), 'utf8'), /<meta name="api-origin" content="https:\/\/api\.example"><\/head>/, shell);
  }
  assert.equal(existsSync(join(out, 'en', 'admin')), false, 'the admin area is Spanish only');
  assert.match(readFileSync(join(out, '_headers'), 'utf8'), /^\/es\/admin\*\n  X-Robots-Tag: noindex/m);
});
