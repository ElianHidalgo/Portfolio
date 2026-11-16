import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';
import { LoginPage } from './admin/login/login-page';
import { routes } from './app.routes';
import { API_ORIGIN } from './core/http/api-origin';
import { BackendWake } from './core/http/backend-wake';
import { NotFoundPage } from './public/not-found/not-found-page';

describe('admin routes', () => {
  function setUp(locale: string): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: locale },
        { provide: API_ORIGIN, useValue: 'https://api.example' },
        { provide: BackendWake, useValue: { wake: vi.fn() } },
      ],
    });
  }

  it('offers the admin login in the Spanish build', async () => {
    setUp('es');
    const harness = await RouterTestingHarness.create();

    expect(await harness.navigateByUrl('/admin/login')).toBeInstanceOf(LoginPage);
  });

  it('does not offer the admin area in the English build', async () => {
    setUp('en-US');
    const harness = await RouterTestingHarness.create();

    expect(await harness.navigateByUrl('/admin/login')).toBeInstanceOf(NotFoundPage);
  });

  it('sends visitors without a session from the panel to the login', async () => {
    sessionStorage.clear();
    setUp('es');
    const harness = await RouterTestingHarness.create();

    expect(await harness.navigateByUrl('/admin')).toBeInstanceOf(LoginPage);
  });
});
