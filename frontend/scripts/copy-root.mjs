// Writes the files that live at the output root (outside the /es/ and /en/ apps):
// - index.html: language picker; the tested pickLocale() source is injected, so the
//   shipped code is the tested code.
// - _redirects / _headers: static-host rules (Cloudflare Pages syntax; ignored by nginx).
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { pickLocale } from './pick-locale.mjs';

const LOCALES = ['es', 'en'];

// Unknown paths of each language get its client-only shell, which shows "not found"
// (or, later, the admin area) instead of the root language picker.
const REDIRECTS = LOCALES.map((locale) => `/${locale}/* /${locale}/index.csr.html 200`).join('\n') + '\n';

/** SHA-256 hashes of every executable inline <script> in the built HTML (CSP allow-list). */
function inlineScriptHashes(outDir) {
  const hashes = new Set();
  for (const file of readdirSync(outDir, { recursive: true })) {
    if (!String(file).endsWith('.html')) continue;
    const html = readFileSync(join(outDir, String(file)), 'utf8');
    for (const [, attributes, code] of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)) {
      const type = /\btype="([^"]*)"/.exec(attributes)?.[1] ?? '';
      const executable = !/\bsrc=/.test(attributes) && ['', 'text/javascript', 'module'].includes(type);
      if (executable && code.trim()) {
        hashes.add(`'sha256-${createHash('sha256').update(code).digest('base64')}'`);
      }
    }
  }
  return [...hashes].sort();
}

// Scripts: only our own files plus the exact inline scripts Angular generated (by hash) —
// no 'unsafe-inline'. Styles: Angular injects component styles at runtime, so inline
// styles are allowed (much lower risk than scripts).
const contentSecurityPolicy = (scriptHashes) =>
  [
    "default-src 'self'",
    `script-src 'self' ${scriptHashes.join(' ')}`.trim(),
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');

const headers = (scriptHashes) => `/*
  Content-Security-Policy: ${contentSecurityPolicy(scriptHashes)}
  Strict-Transport-Security: max-age=31536000; includeSubDomains
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  X-Frame-Options: DENY
  Permissions-Policy: camera=(), microphone=(), geolocation=()

# Hashed build output never changes: cache it for a year.
/*.js
  Cache-Control: public, max-age=31536000, immutable
/*.css
  Cache-Control: public, max-age=31536000, immutable
/*/media/*
  Cache-Control: public, max-age=31536000, immutable
`;

export function writeRootFiles(outDir, env = process.env) {
  const siteUrl = (env.SITE_URL || 'http://localhost:8080').replace(/\/+$/, '');
  const template = readFileSync(new URL('../root/index.html', import.meta.url), 'utf8');
  const html = template.replace('<!-- pick-locale -->', pickLocale.toString()).replaceAll('{{SITE_URL}}', siteUrl);

  writeFileSync(join(outDir, 'index.html'), html);
  writeFileSync(join(outDir, '_redirects'), REDIRECTS);
  // Last: the hashes must cover every page, including the root page written above.
  writeFileSync(join(outDir, '_headers'), headers(inlineScriptHashes(outDir)));
}

// CLI: node scripts/copy-root.mjs
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  writeRootFiles(fileURLToPath(new URL('../dist/frontend/browser', import.meta.url)));
  console.log('Root files written (index.html, _redirects, _headers).');
}
