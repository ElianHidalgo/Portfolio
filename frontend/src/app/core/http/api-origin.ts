import { DOCUMENT } from '@angular/common';
import { EnvironmentProviders, InjectionToken, Provider, inject, provideAppInitializer } from '@angular/core';
import { Meta } from '@angular/platform-browser';

/**
 * Origin of the API for requests the browser makes itself (the contact form), without a
 * trailing slash; `''` means same-origin relative URLs.
 *
 * Prerender: `API_URL`, which {@link provideServerApiOrigin} also writes into the page as
 * `<meta name="api-origin">`. Browser: read from that tag. Pages without it (client-only
 * shells, the local Docker build behind nginx) use relative `/api` URLs.
 */
export const API_ORIGIN = new InjectionToken<string>('API_ORIGIN', {
  providedIn: 'root',
  factory: () => inject(DOCUMENT).head.querySelector('meta[name="api-origin"]')?.getAttribute('content') ?? '',
});

/** Server-only: the build's API URL, published to the browser through the prerendered page. */
export function provideServerApiOrigin(apiUrl: string): (Provider | EnvironmentProviders)[] {
  return [
    { provide: API_ORIGIN, useValue: apiUrl },
    provideAppInitializer(() => {
      inject(Meta).updateTag({ name: 'api-origin', content: apiUrl });
    }),
  ];
}
