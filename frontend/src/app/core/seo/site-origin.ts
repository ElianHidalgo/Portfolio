import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

/**
 * Public origin of the site (no trailing slash), for absolute canonical/hreflang/og URLs.
 *
 * Prerender: `SITE_URL`, provided in `app.config.server.ts`.
 * Browser: the origin of the canonical link written at build time, so a copy of the site
 * served from another host (e.g. a preview domain) still points search engines at the
 * real one; falls back to the current origin when the page has no canonical link.
 */
export const SITE_ORIGIN = new InjectionToken<string>('SITE_ORIGIN', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    const canonical = document.head.querySelector('link[rel="canonical"]')?.getAttribute('href');
    return canonical ? new URL(canonical).origin : document.location.origin;
  },
});
