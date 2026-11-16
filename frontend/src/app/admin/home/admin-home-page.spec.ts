import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../core/http/api-origin';
import { AdminHomePage } from './admin-home-page';

describe('AdminHomePage', () => {
  it('shows who is signed in', async () => {
    TestBed.configureTestingModule({
      imports: [AdminHomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_ORIGIN, useValue: 'https://api.example' }],
    });
    const fixture = TestBed.createComponent(AdminHomePage);
    // The resource request stays pending until flushed, so wait for it with tick(), not whenStable().
    TestBed.tick();

    TestBed.inject(HttpTestingController)
      .expectOne('https://api.example/api/admin/session')
      .flush({ username: 'admin', expiresAt: new Date(Date.now() + 3_600_000).toISOString() });
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sesión iniciada como admin');
  });
});
