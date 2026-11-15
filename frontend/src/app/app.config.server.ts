import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { buildEnv } from './core/build-env';
import { API_BASE_URL, serverApiUrlInterceptor } from './core/http/server-api-url.interceptor';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    provideHttpClient(withFetch(), withInterceptors([serverApiUrlInterceptor])),
    { provide: API_BASE_URL, useValue: buildEnv.apiUrl },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
