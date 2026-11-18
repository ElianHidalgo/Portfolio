// Writes the files that live at the output root (outside the /es/ and /en/ apps):
// - index.html: language picker; the tested pickLocale() source is injected, so the
//   shipped code is the tested code.
// - 404.html pages: Cloudflare (assets with not_found_handling "404-page") serves the
//   nearest 404.html only when no file matches, so prerendered pages always win. Each
//   locale's 404 is its client-only shell, which shows "not found"; the root 404 is the
//   language picker.
// - es/admin/ and its pages (login, mensajes, perfil, skills): the client-only shell again, so the (Spanish-only) admin
//   area answers 200 instead of the 404 page. Every shell written here carries
//   <meta name="api-origin"> when API_URL is set, so the browser can reach the API from it.
// - _headers: security and cache headers (Cloudflare syntax; ignored by nginx).
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { pickLocale } from './pick-locale.mjs';

const LOCALES = ['es', 'en'];

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
const contentSecurityPolicy = (scriptHashes, apiOrigin) =>
  [
    "default-src 'self'",
    `script-src 'self' ${scriptHashes.join(' ')}`.trim(),
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    "font-src 'self'",
    // The browser calls the API itself only for the contact form (and its wake-up ping).
    `connect-src 'self' ${apiOrigin}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');

const headers = (scriptHashes, apiOrigin) => `/*
  Content-Security-Policy: ${contentSecurityPolicy(scriptHashes, apiOrigin)}
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
${LOCALES.map((locale) => `/${locale}/media/*\n  Cache-Control: public, max-age=31536000, immutable`).join('\n')}

# Client-only shells and 404 pages carry no content of their own: keep them out of search results.
${[...LOCALES.flatMap((locale) => [`/${locale}/index.csr*`, `/${locale}/404*`]), '/404*', '/es/admin*']
  .map((path) => `${path}\n  X-Robots-Tag: noindex`)
  .join('\n')}
`;

export function writeRootFiles(outDir, env = process.env) {
  const siteUrl = (env.SITE_URL || 'http://localhost:8080').replace(/\/+$/, '');
  const template = readFileSync(new URL('../root/index.html', import.meta.url), 'utf8');
  const html = template.replace('<!-- pick-locale -->', pickLocale.toString()).replaceAll('{{SITE_URL}}', siteUrl);

  writeFileSync(join(outDir, 'index.html'), html);
  writeFileSync(join(outDir, '404.html'), html);
  const shellApiOrigin = env.API_URL ? new URL(env.API_URL).origin : null;
  const writeShell = (shell, target) => {
    const html = readFileSync(shell, 'utf8');
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, shellApiOrigin
      ? html.replace('</head>', `<meta name="api-origin" content="${shellApiOrigin}"></head>`)
      : html);
  };
  for (const locale of LOCALES) {
    const shell = join(outDir, locale, 'index.csr.html');
    if (!existsSync(shell)) continue;
    writeShell(shell, join(outDir, locale, '404.html'));
    if (locale === 'es') {
      for (const page of ['admin', 'admin/login', 'admin/mensajes', 'admin/perfil', 'admin/skills']) writeShell(shell, join(outDir, locale, page, 'index.html'));
    }
  }
  // Last: the hashes must cover every page, including the root page written above.
  const apiOrigin = new URL(env.API_URL || 'http://localhost:8080').origin;
  writeFileSync(join(outDir, '_headers'), headers(inlineScriptHashes(outDir), apiOrigin));
}

// CLI: node scripts/copy-root.mjs
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  writeRootFiles(fileURLToPath(new URL('../dist/frontend/browser', import.meta.url)));
  console.log('Root files written (index.html, 404 pages, admin shells, _headers).');
}
