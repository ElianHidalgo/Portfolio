import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { AdminMessage } from '../api/admin-models';
import { MessagesPage } from './messages-page';

const API = 'https://api.example/api/admin/messages';

function message(id: number, overrides: Partial<AdminMessage> = {}): AdminMessage {
  return {
    id,
    name: `Person ${id}`,
    email: `p${id}@example.com`,
    message: `Message number ${id}.`,
    locale: 'es',
    createdAt: `2026-11-1${id}T10:00:00Z`,
    read: false,
    ...overrides,
  };
}

describe('MessagesPage', () => {
  let fixture: ComponentFixture<MessagesPage>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const items = () => Array.from(el().querySelectorAll('.inbox__item')) as HTMLElement[];
  const button = (item: HTMLElement, text: string) =>
    Array.from(item.querySelectorAll('button')).find((b) => b.textContent?.includes(text)) as HTMLButtonElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function open(firstPage: AdminMessage[], total = firstPage.length): Promise<void> {
    TestBed.configureTestingModule({
      imports: [MessagesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_ORIGIN, useValue: 'https://api.example' }],
    });
    fixture = TestBed.createComponent(MessagesPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}?page=0&size=20`).flush({ items: firstPage, total, unread: firstPage.filter((m) => !m.read).length });
    await render();
  }

  afterEach(() => vi.restoreAllMocks());

  it('lists messages newest first with reply links', async () => {
    await open([message(3), message(2, { read: true }), message(1)]);

    expect(items().map((i) => i.querySelector('.inbox__name')?.textContent?.trim())).toEqual(['Person 3', 'Person 2', 'Person 1']);
    expect(items()[0].querySelector('a[href="mailto:p3@example.com"]')?.textContent).toContain('Responder');
    expect(items()[0].textContent).toContain('Nuevo');
    expect(items()[1].textContent).not.toContain('Nuevo');
  });

  it('says when the inbox is empty', async () => {
    await open([]);

    expect(el().textContent).toContain('No hay mensajes.');
  });

  it('rendersMessageTextLiterally', async () => {
    await open([message(1, { message: '<img src=x onerror=alert(1)>\nsecond line' })]);

    expect(items()[0].querySelector('img')).toBeNull();
    expect(items()[0].querySelector('.inbox__text')?.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('marks a message read and unread', async () => {
    await open([message(1)]);

    button(items()[0], 'Marcar como leído').click();
    const request = http.expectOne(`${API}/1/read`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ read: true });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await render();
    expect(items()[0].textContent).not.toContain('Nuevo');

    button(items()[0], 'Marcar como no leído').click();
    http.expectOne(`${API}/1/read`).flush(null, { status: 204, statusText: 'No Content' });
    await render();
    expect(items()[0].textContent).toContain('Nuevo');
  });

  it('deletes after confirmation only', async () => {
    await open([message(2), message(1)]);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);

    button(items()[0], 'Eliminar').click();
    expect(confirm).toHaveBeenCalledWith('¿Eliminar este mensaje? No se puede deshacer.');
    http.expectNone(`${API}/2`);

    confirm.mockReturnValue(true);
    button(items()[0], 'Eliminar').click();
    const request = http.expectOne(`${API}/2`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await render();

    expect(items().length).toBe(1);
  });

  it('loads more pages', async () => {
    await open([message(3), message(2)], 3);

    const more = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Cargar más'))!;
    more.click();
    http.expectOne(`${API}?page=1&size=20`).flush({ items: [message(1)], total: 3, unread: 3 });
    await render();

    expect(items().length).toBe(3);
    expect(Array.from(el().querySelectorAll('button')).some((b) => b.textContent?.includes('Cargar más'))).toBe(false);
  });
});
