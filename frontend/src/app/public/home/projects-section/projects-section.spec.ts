import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProjectSummary } from '../../../core/models/project';
import { ProjectsSection } from './projects-section';

function project(slug: string, overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return {
    slug,
    title: { es: `Proyecto ${slug}`, en: `Project ${slug}` },
    summary: { es: 'Resumen.', en: 'Summary.' },
    technologies: ['Java', 'Spring Boot'],
    repoUrl: null,
    demoUrl: null,
    imageUrl: null,
    featured: false,
    ...overrides,
  };
}

describe('ProjectsSection', () => {
  let fixture: ComponentFixture<ProjectsSection>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const texts = (selector: string) =>
    Array.from(el().querySelectorAll(selector)).map((n) => n.textContent?.trim());

  async function respond(body: ProjectSummary[] | null, status = 200): Promise<void> {
    const request = http.expectOne('/api/projects');
    if (status === 200) {
      request.flush(body);
    } else {
      request.flush({ title: 'Error' }, { status, statusText: 'Error' });
    }
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProjectsSection],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectsSection);
    TestBed.tick();
  });

  afterEach(() => http.verify());

  it('renders each project with a link to its page', async () => {
    await respond([project('a'), project('b')]);

    const links = Array.from(el().querySelectorAll('#projects h3 a'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/projects/a', '/projects/b']);
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Project a', 'Project b']);
  });

  it('shows technologies and only the links that exist', async () => {
    await respond([project('a', { repoUrl: 'https://github.com/x/a' })]);

    expect(el().querySelector('#projects')?.textContent).toContain('Spring Boot');
    expect(texts('#projects a')).toContain('Code');
    expect(texts('#projects a')).not.toContain('Live demo');
    expect(el().querySelector('img')).toBeNull();
  });

  it('renders nothing when there are no projects', async () => {
    await respond([]);
    expect(el().querySelector('#projects')).toBeNull();
  });

  it('shows its own error and retry when projects fail', async () => {
    await respond(null, 500);
    expect(el().textContent).toContain('Projects didn’t load.');

    const retry = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Try again');
    retry!.click();
    TestBed.tick();
    await respond([project('a')]);

    expect(texts('#projects h3 a')).toEqual(['Project a']);
  });
});
