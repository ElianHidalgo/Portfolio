import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_ORIGIN } from '../../core/http/api-origin';
import { formatAdminDate } from '../api/admin-dates';
import { AdminHomePage } from './admin-home-page';

const API = 'https://api.example/api/admin';
const PUBLISHED = '2026-11-18T15:30:00Z';

describe('AdminHomePage', () => {
  let fixture: ComponentFixture<AdminHomePage>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const publishButton = () => el().querySelector('button.button--primary') as HTMLButtonElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AdminHomePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
      ],
    });
    fixture = TestBed.createComponent(AdminHomePage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/messages?page=0&size=1`).flush({ items: [], total: 5, unread: 2 });
    http.expectOne(`${API}/publish/status`).flush({ configured: true, lastRequestedAt: PUBLISHED });
    await render();
  });

  async function clickPublish(): Promise<void> {
    publishButton().click();
    await render();
  }

  it('shows unread count and last publication', () => {
    expect(el().textContent).toContain('Mensajes sin leer: 2');
    expect(el().textContent).toContain(`Última publicación: ${formatAdminDate(PUBLISHED)}`);
  });

  it('publishes and shows the result', async () => {
    await clickPublish();
    const request = http.expectOne(`${API}/publish`);
    expect(request.request.method).toBe('POST');
    request.flush({ configured: true, lastRequestedAt: '2026-11-18T16:00:00Z' }, { status: 202, statusText: 'Accepted' });
    await render();

    expect(el().textContent).toContain('Publicación solicitada. El sitio se actualizará en 1–3 minutos.');
    expect(el().textContent).toContain(formatAdminDate('2026-11-18T16:00:00Z'));
  });

  it('disablesPublishWhileSending', async () => {
    await clickPublish();
    expect(publishButton().disabled).toBe(true);

    http.expectOne(`${API}/publish`).flush({ configured: true, lastRequestedAt: PUBLISHED }, { status: 202, statusText: 'Accepted' });
    await render();
    expect(publishButton().disabled).toBe(false);
  });

  it('explains 429, 503 and failures', async () => {
    const cases: Array<[number, string]> = [
      [429, 'Ya hay una publicación en curso. Espera un minuto.'],
      [503, 'La publicación no está configurada (falta el deploy hook).'],
      [502, 'No se pudo publicar. Inténtalo de nuevo.'],
    ];
    for (const [status, text] of cases) {
      await clickPublish();
      http.expectOne(`${API}/publish`).flush({}, { status, statusText: String(status) });
      await render();
      expect(el().textContent).toContain(text);
    }
  });

  it('says when the site was never published', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AdminHomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: API_ORIGIN, useValue: 'https://api.example' }],
    });
    fixture = TestBed.createComponent(AdminHomePage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/messages?page=0&size=1`).flush({ items: [], total: 0, unread: 0 });
    http.expectOne(`${API}/publish/status`).flush({ configured: true, lastRequestedAt: null });
    await render();

    expect(el().textContent).toContain('Aún no se ha publicado');
  });
});
