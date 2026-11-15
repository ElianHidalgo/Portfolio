import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL, serverApiUrlInterceptor } from './server-api-url.interceptor';

describe('serverApiUrlInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverApiUrlInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('prefixes API calls with the build-time API URL', () => {
    http.get('/api/profile').subscribe();
    controller.expectOne('http://api.test/api/profile').flush({});
  });

  it('leaves other URLs alone', () => {
    http.get('/assets/x.json').subscribe();
    controller.expectOne('/assets/x.json').flush({});
  });
});
