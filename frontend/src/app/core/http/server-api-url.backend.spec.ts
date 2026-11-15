import { HttpBackend, HttpEvent, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, firstValueFrom, of } from 'rxjs';
import { ServerApiUrlBackend } from './server-api-url.backend';

class RecordingBackend implements HttpBackend {
  readonly urls: string[] = [];
  handle(request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    this.urls.push(request.url);
    return of(new HttpResponse({ status: 200 }));
  }
}

describe('ServerApiUrlBackend', () => {
  it('sends API calls to the build-time API URL', async () => {
    const inner = new RecordingBackend();
    await firstValueFrom(new ServerApiUrlBackend(inner, 'http://api.test').handle(new HttpRequest('GET', '/api/profile')));
    expect(inner.urls).toEqual(['http://api.test/api/profile']);
  });

  it('also rewrites API calls that the server platform already made absolute', async () => {
    const inner = new RecordingBackend();
    const request = new HttpRequest('GET', 'http://localhost/api/projects?x=1');
    await firstValueFrom(new ServerApiUrlBackend(inner, 'http://api.test').handle(request));
    expect(inner.urls).toEqual(['http://api.test/api/projects?x=1']);
  });

  it('leaves other URLs alone', async () => {
    const inner = new RecordingBackend();
    await firstValueFrom(new ServerApiUrlBackend(inner, 'http://api.test').handle(new HttpRequest('GET', '/assets/x.json')));
    expect(inner.urls).toEqual(['/assets/x.json']);
  });
});
