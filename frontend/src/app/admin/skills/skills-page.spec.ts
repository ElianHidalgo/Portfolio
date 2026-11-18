import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { Profile } from '../../core/models/profile';
import { SkillsPage } from './skills-page';

const API = 'https://api.example/api/admin';

const PROFILE: Profile = {
  fullName: 'Victor Hidalgo',
  headline: { es: 'D', en: 'D' },
  about: { es: 'A', en: 'A' },
  location: null,
  email: null,
  githubUrl: null,
  linkedinUrl: null,
  avatarUrl: null,
  cvUrl: { es: null, en: null },
  skills: [
    { id: 1, name: 'React', category: 'FRONTEND' },
    { id: 2, name: 'Java', category: 'BACKEND' },
    { id: 3, name: 'Spring', category: 'BACKEND' },
  ],
};

describe('SkillsPage', () => {
  let fixture: ComponentFixture<SkillsPage>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const rows = () => Array.from(el().querySelectorAll<HTMLElement>('.skills__row'));
  const names = () => rows().map((row) => (row.querySelector('input') as HTMLInputElement).value);
  const headings = () => Array.from(el().querySelectorAll('h2')).map((h) => h.textContent?.trim());
  const button = (row: HTMLElement, label: string) =>
    Array.from(row.querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [SkillsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
      ],
    });
    fixture = TestBed.createComponent(SkillsPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/profile`).flush(PROFILE);
    await render();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    http.verify();
  });

  it('groups skills by category in the fixed order', () => {
    expect(headings().slice(0, 2)).toEqual(['Backend', 'Frontend']);
    expect(headings()).toContain('Base de datos');
    expect(names()).toEqual(['Java', 'Spring', 'React']);
    expect(el().textContent).toContain('Sin skills en esta categoría.');
    expect(el().textContent).toContain('Los cambios se verán en el sitio cuando pulses Publicar en Inicio.');
  });

  async function addKotlin(): Promise<void> {
    const name = el().querySelector('#new-skill-name') as HTMLInputElement;
    name.value = 'Kotlin';
    name.dispatchEvent(new Event('input'));
    const category = el().querySelector('#new-skill-category') as HTMLSelectElement;
    category.value = 'BACKEND';
    category.dispatchEvent(new Event('change'));
    await render();
    (el().querySelector('form button[type="submit"]') as HTMLButtonElement).click();
    await render();
  }

  it('adds a skill', async () => {
    await addKotlin();
    const request = http.expectOne(`${API}/skills`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Kotlin', category: 'BACKEND' });
    request.flush({ id: 4, name: 'Kotlin', category: 'BACKEND', displayOrder: 2 });
    await render();
    expect(names()).toEqual(['Java', 'Spring', 'Kotlin', 'React']);
  });

  it('explainsADuplicateName', async () => {
    await addKotlin();
    http.expectOne(`${API}/skills`).flush({}, { status: 409, statusText: 'Conflict' });
    await render();
    expect(el().textContent).toContain('Ya existe una skill con ese nombre.');
  });

  it('shows a generic failure', async () => {
    await addKotlin();
    http.expectOne(`${API}/skills`).flush({}, { status: 500, statusText: 'Server Error' });
    await render();
    expect(el().textContent).toContain('No se pudo guardar. Inténtalo de nuevo.');
  });

  it('renames and recategorizes', async () => {
    const row = rows()[2];
    expect(button(row, 'Guardar').disabled).toBe(true);
    const name = row.querySelector('input') as HTMLInputElement;
    name.value = 'Preact';
    name.dispatchEvent(new Event('input'));
    const category = row.querySelector('select') as HTMLSelectElement;
    category.value = 'TOOLS';
    category.dispatchEvent(new Event('change'));
    await render();
    button(rows()[2], 'Guardar').click();
    await render();
    const request = http.expectOne(`${API}/skills/1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Preact', category: 'TOOLS' });
    expect(button(rows()[2], 'Eliminar').disabled).toBe(true);
    request.flush({ id: 1, name: 'Preact', category: 'TOOLS', displayOrder: 0 });
    await render();
    expect(headings()).toContain('Herramientas');
    expect(names()).toEqual(['Java', 'Spring', 'Preact']);
  });

  it('moves a skill up within its category', async () => {
    expect(button(rows()[0], '↑').disabled).toBe(true);
    expect(button(rows()[1], '↓').disabled).toBe(true);
    button(rows()[1], '↑').click();
    await render();
    const request = http.expectOne(`${API}/skills/order`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ ids: [1, 3, 2] });
    request.flush([
      { id: 1, name: 'React', category: 'FRONTEND', displayOrder: 0 },
      { id: 3, name: 'Spring', category: 'BACKEND', displayOrder: 1 },
      { id: 2, name: 'Java', category: 'BACKEND', displayOrder: 2 },
    ]);
    await render();
    expect(names()).toEqual(['Spring', 'Java', 'React']);
  });

  it('reloads after a stale reorder', async () => {
    button(rows()[1], '↑').click();
    await render();
    http.expectOne(`${API}/skills/order`).flush({}, { status: 400, statusText: 'Bad Request' });
    await render();
    http.expectOne(`${API}/profile`).flush({ ...PROFILE, skills: [PROFILE.skills[0]] });
    await render();
    expect(el().textContent).toContain('La lista cambió en otra pestaña. Se recargó.');
    expect(names()).toEqual(['React']);
  });

  it('deletes after confirmation only', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    button(rows()[0], 'Eliminar').click();
    await render();
    expect(confirm).toHaveBeenCalledWith('¿Eliminar esta skill?');
    http.expectNone(`${API}/skills/2`);
    confirm.mockReturnValue(true);
    button(rows()[0], 'Eliminar').click();
    await render();
    const request = http.expectOne(`${API}/skills/2`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await render();
    expect(names()).toEqual(['Spring', 'React']);
  });
});
