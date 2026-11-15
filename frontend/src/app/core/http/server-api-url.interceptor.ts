import { HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';

/** Absolute base URL of the API, used only while prerendering (no browser origin exists there). */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

/**
 * During the build the app runs in Node, where relative URLs like `/api/profile` have no
 * origin. This interceptor (registered only in the server config) makes them absolute.
 * In the browser the app keeps relative URLs, so the same code works behind any host.
 */
export const serverApiUrlInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  return next(request.clone({ url: `${inject(API_BASE_URL)}${request.url}` }));
};
