import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Profile } from '../../core/models/profile';
import { HomePage } from './home-page';

function sampleProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    fullName: 'Ada Lovelace',
    headline: { es: 'Desarrolladora', en: 'Software developer' },
    about: { es: 'Hola.', en: 'Hello.' },
    location: 'London',
    email: 'ada@example.com',
    githubUrl: 'https://github.com/ada',
    linkedinUrl: 'https://linkedin.com/in/ada',
    avatarUrl: 'https://example.com/ada.png',
    cvUrl: { es: 'https://example.com/cv-es.pdf', en: 'https://example.com/cv-en.pdf' },
    skills: [{ name: 'Java', category: 'BACKEND' }],
    ...overrides,
  };
}

describe('HomePage', () => {
  let fixture: ComponentFixture<HomePage>;
  let http: HttpTestingController;

  const page = () => fixture.nativeElement as HTMLElement;
  const texts = (selector: string) =>
    Array.from(page().querySelectorAll(selector)).map((el) => el.textContent?.trim());

  async function respond(body: Profile | null, status = 200): Promise<void> {
    const request = http.expectOne('/api/profile');
    if (status === 200) {
      request.flush(body);
    } else {
      request.flush({ title: 'Error' }, { status, statusText: 'Error' });
    }
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HomePage);
    // Not whenStable(): a pending request keeps the app unstable until it is flushed.
    TestBed.tick();
  });

  afterEach(() => http.verify());

  it('shows a loading message until the profile arrives', () => {
    expect(page().textContent).toContain('Loading profile…');
    http.expectOne('/api/profile');
  });

  it('renders name, headline and each paragraph of the about text', async () => {
    await respond(sampleProfile({ about: { es: 'Uno.', en: 'First paragraph.\n\nSecond paragraph.' } }));

    expect(page().querySelector('#hero h1')?.textContent?.trim()).toBe('Ada Lovelace');
    expect(page().querySelector('#hero')?.textContent).toContain('Software developer');
    expect(texts('#about p')).toEqual(['First paragraph.', 'Second paragraph.']);
  });

  it('groups skills under translated category headings', async () => {
    await respond(
      sampleProfile({
        skills: [
          { name: 'Java', category: 'BACKEND' },
          { name: 'Docker', category: 'DEVOPS' },
        ],
      }),
    );

    expect(texts('#skills h3')).toEqual(['Backend', 'DevOps']);
    expect(texts('#skills li')).toEqual(['Java', 'Docker']);
  });

  it('hides optional fields that are null', async () => {
    await respond(
      sampleProfile({
        location: null,
        email: null,
        linkedinUrl: null,
        avatarUrl: null,
        cvUrl: { es: null, en: null },
        skills: [],
      }),
    );

    expect(page().querySelector('img')).toBeNull();
    expect(page().querySelector('a[href^="mailto:"]')).toBeNull();
    expect(texts('a')).not.toContain('LinkedIn');
    expect(texts('a')).not.toContain('Download CV');
    expect(page().querySelector('#skills')).toBeNull();
    expect(page().textContent).not.toContain('null');
  });

  it('shows an error with a working retry when the request fails', async () => {
    await respond(null, 500);
    expect(page().textContent).toContain('The profile didn’t load. Try again in a few seconds.');

    const retry = Array.from(page().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Try again',
    );
    retry!.click();
    TestBed.tick();
    await respond(sampleProfile());

    expect(page().querySelector('#hero h1')?.textContent?.trim()).toBe('Ada Lovelace');
  });

  it('shows the error state when the profile does not exist', async () => {
    await respond(null, 404);
    expect(page().textContent).toContain('The profile didn’t load. Try again in a few seconds.');
  });
});
