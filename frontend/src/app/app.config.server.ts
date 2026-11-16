import { FetchBackend, HttpBackend } from '@angular/common/http';
import { ApplicationConfig, inject, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { buildEnv } from './core/build-env';
import { provideServerApiOrigin } from './core/http/api-origin';
import { ServerApiUrlBackend } from './core/http/server-api-url.backend';
import { SITE_ORIGIN } from './core/seo/site-origin';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    // Overrides the browser config's backend only while prerendering.
    { provide: HttpBackend, useFactory: () => new ServerApiUrlBackend(inject(FetchBackend), buildEnv.apiUrl) },
    { provide: SITE_ORIGIN, useValue: buildEnv.siteUrl },
    ...provideServerApiOrigin(buildEnv.apiUrl),
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
