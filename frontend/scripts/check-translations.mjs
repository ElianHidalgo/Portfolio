// Fails when a unit in the Spanish translation file has no <target>.
// Angular silently falls back to the English source in that case.
import { readFileSync } from 'node:fs';

const file = new URL('../src/locale/messages.es.xlf', import.meta.url);
const xlf = readFileSync(file, 'utf8');
const units = [...xlf.matchAll(/<trans-unit id="([^"]+)"[\s\S]*?<\/trans-unit>/g)];
const untranslated = units
  .filter(([unit]) => !/<target>\s*\S[\s\S]*?<\/target>/.test(unit))
  .map(([, id]) => id);

if (untranslated.length > 0) {
  console.error(`Missing Spanish <target> for: ${untranslated.join(', ')}`);
  process.exit(1);
}
console.log(`Translations OK (${units.length} units).`);
