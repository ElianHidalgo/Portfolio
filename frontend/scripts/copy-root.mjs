// Writes the root language page (dist/frontend/browser/index.html).
// Injects the tested pickLocale() source and SITE_URL, so the shipped code is the tested code.
import { readFileSync, writeFileSync } from 'node:fs';
import { pickLocale } from './pick-locale.mjs';

const siteUrl = (process.env.SITE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const template = readFileSync(new URL('../root/index.html', import.meta.url), 'utf8');

const html = template
  .replace('<!-- pick-locale -->', pickLocale.toString())
  .replaceAll('{{SITE_URL}}', siteUrl);

writeFileSync(new URL('../dist/frontend/browser/index.html', import.meta.url), html);
console.log('Root language page written.');
