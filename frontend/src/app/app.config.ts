import { ViewportScroller } from '@angular/common';
import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideClientHydration, withEventReplay, withI18nSupport } from '@angular/platform-browser';
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
    // Reuses the prerendered DOM and the build-time HTTP responses (transfer cache):
    // the first visit does not call the API. Clicks before hydration are replayed.
    // withI18nSupport is required: without it, components containing i18n blocks are not
    // hydrated and the browser silently redraws the whole page.
    provideClientHydration(withEventReplay(), withI18nSupport()),
  ],
};
