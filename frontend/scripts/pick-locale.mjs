/**
 * Chooses the site language from the browser's preferred languages.
 * Shipped inline in the root page by copy-root.mjs, so it must stay dependency-free.
 */
export function pickLocale(languages) {
  for (const language of languages) {
    const primary = String(language).toLowerCase().split('-')[0];
    if (primary === 'es' || primary === 'en') {
      return primary;
    }
  }
  return 'es';
}
