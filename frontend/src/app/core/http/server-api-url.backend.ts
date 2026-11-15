import { HttpBackend, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Prerender-only HTTP backend: sends `/api/*` requests to the build-time API URL.
 *
 * Why a backend and not an interceptor: interceptors run before Angular's transfer cache,
 * so rewriting there would store the build-time responses under the absolute URL while the
 * browser looks them up under `/api/...` — and every first visit would call the API again.
 * At the backend level the whole chain (including the cache key) still sees `/api/...`.
 */
export class ServerApiUrlBackend implements HttpBackend {
  constructor(
    private readonly inner: HttpBackend,
    private readonly apiBaseUrl: string,
  ) {}

  handle(request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    // Match on the path: by now the server platform may have turned `/api/x` into
    // `http://localhost/api/x` (its relative-URL interceptor runs before the backend).
    const url = new URL(request.url, 'http://relative.invalid');
    if (!url.pathname.startsWith('/api/')) {
      return this.inner.handle(request);
    }
    return this.inner.handle(request.clone({ url: `${this.apiBaseUrl}${url.pathname}${url.search}` }));
  }
}
