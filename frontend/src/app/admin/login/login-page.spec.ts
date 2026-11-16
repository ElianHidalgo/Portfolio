import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { BackendWake } from '../../core/http/backend-wake';
import { AdminSession } from '../auth/admin-session';
import { fakeJwt } from '../auth/fake-jwt.spec-helper';
import { LoginPage } from './login-page';

const LOGIN_URL = 'https://api.example/api/auth/login';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let http: HttpTestingController;
  const wake = { wake: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  const el = () => fixture.nativeElement as HTMLElement;
  const form = () => el().querySelector('[data-form-state]') as HTMLElement;
  const field = (name: string) => el().querySelector(`[name="${name}"]`) as HTMLInputElement;
  const button = () => el().querySelector('button[type="submit"]') as HTMLButtonElement;

  function type(name: string, value: string): void {
    field(name).value = value;
    field(name).dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function signIn(): Promise<void> {
    type('username', 'admin');
    type('password', 'secret-password');
    await submit();
  }

  async function answer(status: number, body: object = {}): Promise<void> {
    http.expectOne(LOGIN_URL).flush(body, { status, statusText: String(status) });
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function create(): Promise<void> {
    fixture = TestBed.createComponent(LoginPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    sessionStorage.clear();
    wake.wake.mockReset();
    router.navigateByUrl.mockReset();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
        { provide: BackendWake, useValue: wake },
        { provide: Router, useValue: router },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('wakes the backend when the page opens and says the first answer may be slow', async () => {
    await create();

    expect(wake.wake).toHaveBeenCalled();
    expect(el().textContent).toContain('El panel puede tardar hasta dos minutos en responder la primera vez.');
  });

  it('asks for both fields before sending', async () => {
    await create();
    await submit();

    http.expectNone(LOGIN_URL);
    expect(el().textContent).toContain('Escribe tu usuario.');
    expect(el().textContent).toContain('Escribe tu contraseña.');
  });

  it('signs in and goes to the panel', async () => {
    await create();
    await signIn();

    const request = http.expectOne(LOGIN_URL);
    expect(request.request.body).toEqual({ username: 'admin', password: 'secret-password' });
    expect(form().dataset['formState']).toBe('sending');
    expect(button().disabled).toBe(true);
    request.flush({ token: fakeJwt(7200), expiresAt: new Date().toISOString() });
    await fixture.whenStable();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
  });

  it('shows a single message for wrong credentials', async () => {
    await create();
    await signIn();
    await answer(401);

    expect(form().dataset['formState']).toBe('invalid');
    expect(el().textContent).toContain('Usuario o contraseña incorrectos.');
    expect(field('username').value).toBe('admin');
    expect(field('password').value).toBe('');
  });

  it('shows the attempts limit on 429', async () => {
    await create();
    await signIn();
    await answer(429);

    expect(form().dataset['formState']).toBe('limited');
    expect(el().textContent).toContain('Demasiados intentos. Espera unos minutos.');
  });

  it('showsWakingHintThenRetryableError', async () => {
    vi.useFakeTimers();
    await create();
    await signIn();
    const request = http.expectOne(LOGIN_URL);

    vi.advanceTimersByTime(5_000);
    fixture.detectChanges();
    expect(el().textContent).toContain('El servidor está despertando; puede tardar hasta dos minutos.');

    vi.advanceTimersByTime(125_000);
    fixture.detectChanges();
    expect(request.cancelled).toBe(true);
    expect(form().dataset['formState']).toBe('error');
    expect(el().textContent).toContain('No se pudo conectar. Inténtalo de nuevo.');
    expect(field('username').value).toBe('admin');
    expect(button().disabled).toBe(false);
  });

  it('showsExpiredNotice', async () => {
    TestBed.inject(AdminSession).logout('expired');
    await create();

    expect(el().textContent).toContain('Tu sesión expiró. Vuelve a iniciar sesión.');
  });

  it('skips the form when already signed in', async () => {
    sessionStorage.setItem('portfolio.admin.token', fakeJwt(3600));
    await create();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
  });
});
