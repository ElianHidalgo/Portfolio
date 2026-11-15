import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

/**
 * Public origin of the site (no trailing slash), for absolute canonical/hreflang/og URLs.
 * Browser: the current origin. Prerender: `SITE_URL`, provided in `app.config.server.ts`.
 */
export const SITE_ORIGIN = new InjectionToken<string>('SITE_ORIGIN', {
  providedIn: 'root',
  factory: () => inject(DOCUMENT).location.origin,
});
