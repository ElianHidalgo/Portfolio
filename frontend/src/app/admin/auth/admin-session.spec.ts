import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../core/http/api-origin';
import { AdminSession } from './admin-session';
import { fakeJwt } from './fake-jwt.spec-helper';

describe('AdminSession', () => {
  let session: AdminSession;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_ORIGIN, useValue: 'https://api.example' }],
    });
    session = TestBed.inject(AdminSession);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => sessionStorage.clear());

  it('stores the token from a successful login and reports it valid until exp', () => {
    const token = fakeJwt(3600);
    let done = false;
    session.login('admin', 'secret-password').subscribe(() => (done = true));

    const request = http.expectOne('https://api.example/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'admin', password: 'secret-password' });
    request.flush({ token, expiresAt: new Date(Date.now() + 3_600_000).toISOString() });

    expect(done).toBe(true);
    expect(session.token()).toBe(token);
    expect(session.isAuthenticated()).toBe(true);
    expect(sessionStorage.getItem('portfolio.admin.token')).toBe(token);
  });

  it('treats an expired or malformed token as signed out', () => {
    sessionStorage.setItem('portfolio.admin.token', fakeJwt(-1));
    expect(session.isAuthenticated()).toBe(false);

    sessionStorage.setItem('portfolio.admin.token', 'garbage');
    expect(session.isAuthenticated()).toBe(false);
  });

  it('forgets the token on logout and remembers why', () => {
    sessionStorage.setItem('portfolio.admin.token', fakeJwt(3600));

    session.logout('expired');

    expect(session.token()).toBeNull();
    expect(session.isAuthenticated()).toBe(false);
    expect(session.lastLogoutReason()).toBe('expired');
  });

  it('keeps no token when the login fails', () => {
    session.login('admin', 'wrong').subscribe({ error: () => undefined });
    http.expectOne('https://api.example/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(session.token()).toBeNull();
  });
});
