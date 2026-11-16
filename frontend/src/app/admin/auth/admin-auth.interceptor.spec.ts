import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { adminAuthInterceptor } from './admin-auth.interceptor';
import { AdminSession } from './admin-session';
import { fakeJwt } from './fake-jwt.spec-helper';

describe('adminAuthInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const router = { navigateByUrl: vi.fn() };
  const token = fakeJwt(3600);

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('portfolio.admin.token', token);
    router.navigateByUrl.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([adminAuthInterceptor])),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => sessionStorage.clear());

  it('adds the bearer token only to admin API calls', () => {
    http.get('https://api.example/api/admin/session').subscribe();
    http.get('/api/profile').subscribe();
    http.get('https://evil.example/api/admin/x').subscribe();
    http.post('https://api.example/api/auth/login', {}).subscribe();

    expect(backend.expectOne('https://api.example/api/admin/session').request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    expect(backend.expectOne('/api/profile').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('https://evil.example/api/admin/x').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('https://api.example/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('clears token and redirects on 401', () => {
    let failed = false;
    http.get('https://api.example/api/admin/session').subscribe({ error: () => (failed = true) });

    backend.expectOne('https://api.example/api/admin/session').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(TestBed.inject(AdminSession).token()).toBeNull();
    expect(TestBed.inject(AdminSession).lastLogoutReason()).toBe('expired');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/login');
  });

  it('leaves other errors to the caller', () => {
    http.get('https://api.example/api/admin/session').subscribe({ error: () => undefined });

    backend.expectOne('https://api.example/api/admin/session').flush({}, { status: 500, statusText: 'Error' });

    expect(TestBed.inject(AdminSession).token()).toBe(token);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
