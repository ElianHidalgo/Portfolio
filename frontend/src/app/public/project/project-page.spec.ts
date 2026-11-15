import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { ProjectDetail } from '../../core/models/project';
import { ProjectPage } from './project-page';

function detail(overrides: Partial<ProjectDetail> = {}): ProjectDetail {
  return {
    slug: 'gamevault',
    title: { es: 'GameVault', en: 'GameVault' },
    summary: { es: 'Resumen.', en: 'A summary.' },
    description: { es: 'Uno.\n\nDos.', en: 'One.\n\nTwo.' },
    technologies: ['C#', '.NET MAUI'],
    repoUrl: 'https://github.com/x/gamevault',
    demoUrl: null,
    imageUrl: null,
    featured: false,
    ...overrides,
  };
}

describe('ProjectPage', () => {
  let fixture: ComponentFixture<ProjectPage>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const linkTexts = () => Array.from(el().querySelectorAll('a')).map((a) => a.textContent?.trim());

  async function respond(body: ProjectDetail | null, status = 200): Promise<void> {
    const request = http.expectOne('/api/projects/gamevault');
    if (status === 200) {
      request.flush(body);
    } else {
      request.flush({ title: 'Not Found' }, { status, statusText: 'Error' });
    }
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProjectPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectPage);
    fixture.componentRef.setInput('slug', 'gamevault');
    TestBed.tick();
  });

  afterEach(() => http.verify());

  it('renders title, summary, technologies and description paragraphs', async () => {
    await respond(detail());

    expect(el().querySelector('h1')?.textContent?.trim()).toBe('GameVault');
    expect(el().textContent).toContain('A summary.');
    expect(el().textContent).toContain('.NET MAUI');
    expect(el().querySelectorAll('article p.description').length).toBe(2);
  });

  it('hides image and missing links', async () => {
    await respond(detail({ repoUrl: null, demoUrl: null, imageUrl: null }));

    expect(el().querySelector('img')).toBeNull();
    expect(linkTexts()).not.toContain('Code');
    expect(linkTexts()).not.toContain('Live demo');
  });

  it('shows a not-found message with a way back on 404', async () => {
    await respond(null, 404);

    expect(el().textContent).toContain('This project doesn’t exist.');
    const back = Array.from(el().querySelectorAll('a')).find((a) => a.textContent?.trim() === 'Back to all projects');
    expect(back?.getAttribute('href')).toBe('/#projects');
  });

  it('uses the translated site title when the project does not exist', async () => {
    TestBed.inject(Title).setTitle('');
    await respond(null, 404);
    expect(TestBed.inject(Title).getTitle()).toBe('Portfolio');
  });

  it('sets the document title from the project', async () => {
    await respond(detail());
    expect(TestBed.inject(Title).getTitle()).toBe('GameVault | Portfolio');
  });

  it('uses a trailing-slash canonical URL, matching the prerendered folder', async () => {
    await respond(detail());
    const canonical = document.head.querySelector('link[rel="canonical"]')?.getAttribute('href');
    expect(canonical?.endsWith('/projects/gamevault/')).toBe(true);
  });
});
