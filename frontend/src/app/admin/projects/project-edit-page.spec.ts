import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { AdminProject } from '../api/admin-models';
import { ProjectEditPage, parseTechnologies } from './project-edit-page';

const API = 'https://api.example/api/admin/projects';

const PROJECT: AdminProject = {
  id: 1,
  slug: 'mi-proyecto',
  title: { es: 'Título', en: 'Title' },
  summary: { es: 'Resumen', en: 'Summary' },
  description: { es: 'Descripción', en: 'Description' },
  technologies: ['Java', 'Spring Boot'],
  repoUrl: 'https://github.com/v/p',
  demoUrl: null,
  imageUrl: 'media/p.png',
  featured: true,
  published: true,
  displayOrder: 1,
};

describe('parseTechnologies', () => {
  it('parsesTechnologies', () => {
    expect(parseTechnologies(' Java, ,Spring Boot ,,')).toEqual(['Java', 'Spring Boot']);
  });
});

describe('ProjectEditPage', () => {
  let fixture: ComponentFixture<ProjectEditPage>;
  let http: HttpTestingController;
  let router: Router;

  const el = () => fixture.nativeElement as HTMLElement;
  const input = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement | HTMLTextAreaElement;
  const submit = () => el().querySelector('button[type="submit"]') as HTMLButtonElement;
  const text = () => el().textContent ?? '';

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function type(id: string, value: string): Promise<void> {
    const field = input(id);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await render();
  }

  async function check(id: string, value: boolean): Promise<void> {
    const field = input(id) as HTMLInputElement;
    field.checked = value;
    field.dispatchEvent(new Event('change'));
    await render();
  }

  async function open(id: string | null): Promise<void> {
    TestBed.configureTestingModule({
      imports: [ProjectEditPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(id !== null ? { id } : {}) } } },
      ],
    });
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectEditPage);
    fixture.detectChanges();
  }

  async function openExisting(): Promise<void> {
    await open('1');
    http.expectOne(`${API}/1`).flush(PROJECT);
    await render();
  }

  async function fillNew(): Promise<void> {
    await type('slug', 'demo-project');
    await type('title-es', 'Demo');
    await type('title-en', 'Demo');
    await type('summary-es', 'Resumen');
    await type('summary-en', 'Summary');
    await type('description-es', 'Descripción');
    await type('description-en', 'Description');
    await type('technologies', 'Java, Angular');
  }

  afterEach(() => http.verify());

  it('creates a project', async () => {
    await open(null);
    await render();
    expect(text()).toContain('Nuevo proyecto');
    expect(input('slug')).not.toBeNull();
    expect((input('published') as HTMLInputElement).checked).toBe(false);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await fillNew();
    submit().click();
    const req = http.expectOne(API);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.slug).toBe('demo-project');
    expect(req.request.body.published).toBe(false);
    expect(req.request.body.technologies).toEqual(['Java', 'Angular']);
    req.flush({ ...PROJECT, id: 9 }, { status: 201, statusText: 'Created' });
    await render();
    expect(navigate).toHaveBeenCalledWith('/admin/proyectos', {
      state: { notice: 'Proyecto creado. Está oculto hasta que marques Publicado.' },
    });
  });

  it('explainsADuplicateSlug', async () => {
    await open(null);
    await render();
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await fillNew();
    submit().click();
    http.expectOne(API).flush({}, { status: 409, statusText: 'Conflict' });
    await render();
    expect(text()).toContain('Ya existe un proyecto con esa URL.');
    expect(input('slug').getAttribute('aria-describedby')).toBe('slug-error');
  });

  it('blocks a bad slug and a duplicate technology', async () => {
    await open(null);
    await render();
    await fillNew();
    await type('slug', 'Mi Proyecto');
    submit().click();
    await render();
    expect(text()).toContain('Solo minúsculas, números y guiones (p. ej. mi-proyecto)');
    await type('slug', 'mi-proyecto');
    await type('technologies', 'Java, java');
    submit().click();
    await render();
    expect(text()).toContain('Tecnología repetida: java');
    http.expectNone(API);
  });

  it('loads and updates a project', async () => {
    await openExisting();
    expect(input('title-es').value).toBe('Título');
    expect(input('technologies').value).toBe('Java, Spring Boot');
    expect(input('slug')).toBeNull();
    expect(text()).toContain('/es/projects/mi-proyecto');
    await type('title-en', 'New title');
    submit().click();
    const req = http.expectOne(`${API}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.slug).toBeUndefined();
    expect(req.request.body.title.en).toBe('New title');
    expect(req.request.body.imageUrl).toBe('media/p.png');
    req.flush({ ...PROJECT, title: { es: 'Título', en: 'New title' } });
    await render();
    expect(text()).toContain('Proyecto guardado.');
  });

  it('explainsTheLastPublishedRuleOnUpdate', async () => {
    await openExisting();
    await check('published', false);
    submit().click();
    http.expectOne(`${API}/1`).flush({}, { status: 409, statusText: 'Conflict' });
    await render();
    expect(text()).toContain('No puedes ocultar el último proyecto publicado.');
    expect(input('published').getAttribute('aria-describedby')).toBe('published-error');
  });

  it('explainsAnUnknownProject', async () => {
    await open('99');
    http.expectOne(`${API}/99`).flush({}, { status: 404, statusText: 'Not Found' });
    await render();
    expect(text()).toContain('Proyecto no encontrado.');
    expect(Array.from(el().querySelectorAll('a')).some((a) => a.textContent?.trim() === 'Volver a proyectos')).toBe(true);
  });

  it('explainsAnInvalidId', async () => {
    for (const bad of ['abc', '']) {
      TestBed.resetTestingModule();
      await open(bad);
      await render();
      expect(text()).toContain('Proyecto no encontrado.');
      expect(text()).not.toContain('Nuevo proyecto');
      http.verify();
    }
  });

  it('confirmsBeforeLeaving', async () => {
    await openExisting();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
    await type('title-es', 'Otro');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
    submit().click();
    http.expectOne(`${API}/1`).flush(PROJECT);
    await render();
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });
});
