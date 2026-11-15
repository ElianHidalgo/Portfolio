import { ViewportScroller } from '@angular/common';
import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // Route params (e.g. :slug) arrive as component inputs.
      withComponentInputBinding(),
      // Header links like "/#projects" scroll to the section, also from another page.
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    provideHttpClient(withFetch()),
    // Anchor scrolling stops below the sticky header (3.5rem) plus a little air.
    provideAppInitializer(() => inject(ViewportScroller).setOffset([0, 80])),
  ],
};
