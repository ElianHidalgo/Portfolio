import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { AdminProject } from '../api/admin-models';
import { ProjectsPage } from './projects-page';

const API = 'https://api.example/api/admin';

const project = (id: number, published = true): AdminProject => ({
  id,
  slug: `slug-${id}`,
  title: { es: `Proyecto ${id}`, en: `Project ${id}` },
  summary: { es: 'S', en: 'S' },
  description: { es: 'D', en: 'D' },
  technologies: ['Java'],
  repoUrl: null,
  demoUrl: null,
  imageUrl: null,
  featured: false,
  published,
  displayOrder: id,
});

const THREE = [project(1), project(2, false), project(3)];

describe('ProjectsPage', () => {
  let fixture: ComponentFixture<ProjectsPage>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const rows = () => Array.from(el().querySelectorAll<HTMLElement>('.projects__row'));
  const titles = () => rows().map((row) => row.querySelector('.projects__title')?.textContent?.trim());
  const button = (row: HTMLElement, label: string) =>
    Array.from(row.querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement;

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ProjectsPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
      ],
    });
    fixture = TestBed.createComponent(ProjectsPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/projects`).flush(THREE);
    await render();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    http.verify();
  });

  it('lists projects with their state', () => {
    expect(titles()).toEqual(['Proyecto 1', 'Proyecto 2', 'Proyecto 3']);
    expect(rows()[1].textContent).toContain('Oculto');
    expect(rows()[0].textContent).toContain('Publicado');
    expect(rows()[0].textContent).toContain('slug-1');
    expect(el().textContent).toContain('Los cambios se verán en el sitio cuando pulses Publicar en Inicio.');
    expect(rows()[1].querySelector('a')?.getAttribute('href')).toContain('editar');
    expect(rows()[1].querySelector('a')?.getAttribute('href')).toContain('id=2');
  });

  it('moves a project down', async () => {
    expect(button(rows()[0], '↑').disabled).toBe(true);
    expect(button(rows()[2], '↓').disabled).toBe(true);
    button(rows()[0], '↓').click();
    const req = http.expectOne(`${API}/projects/order`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ ids: [2, 1, 3] });
    req.flush([project(2, false), project(1), project(3)]);
    await render();
    expect(titles()).toEqual(['Proyecto 2', 'Proyecto 1', 'Proyecto 3']);
  });

  it('reloads after a stale reorder', async () => {
    button(rows()[0], '↓').click();
    http.expectOne(`${API}/projects/order`).flush(null, { status: 400, statusText: 'Bad Request' });
    http.expectOne(`${API}/projects`).flush([project(3), project(1)]);
    await render();
    expect(titles()).toEqual(['Proyecto 3', 'Proyecto 1']);
    expect(el().textContent).toContain('La lista cambió en otra pestaña. Se recargó.');
  });

  it('deletes after confirmation only', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    button(rows()[1], 'Eliminar').click();
    expect(confirm).toHaveBeenCalledWith('¿Eliminar este proyecto? No se puede deshacer.');
    http.expectNone(`${API}/projects/2`);

    confirm.mockReturnValue(true);
    button(rows()[1], 'Eliminar').click();
    const req = http.expectOne(`${API}/projects/2`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await render();
    expect(titles()).toEqual(['Proyecto 1', 'Proyecto 3']);
  });

  it('explainsTheLastPublishedRule', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    button(rows()[0], 'Eliminar').click();
    http.expectOne(`${API}/projects/1`).flush(null, { status: 409, statusText: 'Conflict' });
    await render();
    expect(el().textContent).toContain('No puedes ocultar ni eliminar el último proyecto publicado.');
    expect(titles()).toHaveLength(3);
  });

  it('shows other failures and disables the row while in flight', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    button(rows()[0], 'Eliminar').click();
    await render();
    expect(button(rows()[0], 'Eliminar').disabled).toBe(true);
    http.expectOne(`${API}/projects/1`).flush(null, { status: 500, statusText: 'Error' });
    await render();
    expect(el().textContent).toContain('No se pudo guardar. Inténtalo de nuevo.');
    expect(button(rows()[0], 'Eliminar').disabled).toBe(false);
  });
});

describe('ProjectsPage (empty)', () => {
  it('says there are no projects yet', async () => {
    TestBed.configureTestingModule({
      imports: [ProjectsPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
      ],
    });
    const fixture = TestBed.createComponent(ProjectsPage);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/projects`).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Todavía no hay proyectos.');
    http.verify();
  });
});

describe('ProjectsPage notice', () => {
  it('shows the notice handed over by the edit page', async () => {
    TestBed.configureTestingModule({
      imports: [ProjectsPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
      ],
    });
    const notice = 'Proyecto creado. Está oculto hasta que marques Publicado.';
    vi.spyOn(TestBed.inject(Router), 'getCurrentNavigation').mockReturnValue({ extras: { state: { notice } } } as never);
    const fixture = TestBed.createComponent(ProjectsPage);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/projects`).flush([project(1)]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(notice);
  });
});
