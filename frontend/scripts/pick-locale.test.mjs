import assert from 'node:assert/strict';
import { test } from 'node:test';
import { pickLocale } from './pick-locale.mjs';

test('first supported browser language wins', () => {
  assert.equal(pickLocale(['en-US', 'es']), 'en');
  assert.equal(pickLocale(['es-EC']), 'es');
  assert.equal(pickLocale(['fr', 'en-GB']), 'en');
});

test('falls back to Spanish', () => {
  assert.equal(pickLocale(['fr-FR', 'de']), 'es');
  assert.equal(pickLocale([]), 'es');
});
