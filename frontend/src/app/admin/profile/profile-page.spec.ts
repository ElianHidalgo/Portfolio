import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../core/http/api-origin';
import { Profile } from '../../core/models/profile';
import { unsavedChangesGuard } from '../unsaved-changes.guard';
import { ProfilePage } from './profile-page';

const API = 'https://api.example/api/admin/profile';

const PROFILE: Profile = {
  fullName: 'Victor Hidalgo',
  headline: { es: 'Desarrollador', en: 'Developer' },
  about: { es: 'Sobre mí', en: 'About me' },
  location: 'Lima',
  email: 'v@example.com',
  githubUrl: 'https://github.com/v',
  linkedinUrl: null,
  avatarUrl: 'media/avatar.jpg',
  cvUrl: { es: 'cv/cv-es.pdf', en: null },
  skills: [],
};

describe('ProfilePage', () => {
  let fixture: ComponentFixture<ProfilePage>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const input = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement | HTMLTextAreaElement;
  const submit = () => el().querySelector('button[type="submit"]') as HTMLButtonElement;

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

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ProfilePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
      ],
    });
    fixture = TestBed.createComponent(ProfilePage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(API).flush(PROFILE);
    await render();
  });

  it('loads the profile into the form', () => {
    expect(input('fullName').value).toBe('Victor Hidalgo');
    expect(input('about-en').value).toBe('About me');
    expect(input('linkedinUrl').value).toBe('');
    expect(el().textContent).toContain('Los cambios se verán en el sitio cuando pulses Publicar en Inicio.');
  });

  it('saves and shows the confirmation', async () => {
    await type('headline-es', 'Ingeniero');
    submit().click();
    await render();
    const request = http.expectOne(API);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.headline.es).toBe('Ingeniero');
    expect(request.request.body.linkedinUrl).toBeNull();
    expect(request.request.body.cvUrl).toEqual({ es: 'cv/cv-es.pdf', en: null });
    request.flush({ ...PROFILE, headline: { es: 'Ingeniero', en: 'Developer' } });
    await render();
    expect(el().textContent).toContain('Perfil guardado.');
  });

  it('blocks unsafe links before sending', async () => {
    await type('githubUrl', 'javascript:x');
    submit().click();
    await render();
    http.expectNone(API);
    expect(el().textContent).toContain('Debe empezar con https://');
  });

  it('blocks path traversal in the cv path', async () => {
    await type('cvUrl-en', '../secret.pdf');
    submit().click();
    await render();
    http.expectNone(API);
    expect(el().textContent).toContain('Ruta dentro del sitio (p. ej. cv/cv-es.pdf) o enlace https://');
  });

  it('shows character counters', () => {
    expect(el().textContent).toContain('9 / 160');
  });

  it('maps a 400 problem to field errors', async () => {
    submit().click();
    await render();
    http.expectOne(API).flush({ detail: 'email: must be a well-formed email address' }, { status: 400, statusText: 'Bad Request' });
    await render();
    expect(el().textContent).toContain('Revisa los campos marcados.');
  });

  it('explainsTooLarge', async () => {
    submit().click();
    await render();
    http.expectOne(API).flush(null, { status: 413, statusText: 'Payload Too Large' });
    await render();
    expect(el().textContent).toContain('El texto es demasiado largo.');
  });

  it('confirmsBeforeLeavingWithUnsavedChanges', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const leave = () => TestBed.runInInjectionContext(() => unsavedChangesGuard(fixture.componentInstance, null!, null!, null!));

    expect(leave()).toBe(true);
    expect(confirm).not.toHaveBeenCalled();

    await type('fullName', 'Otro');
    expect(leave()).toBe(false);
    expect(confirm).toHaveBeenCalledWith('Tienes cambios sin guardar. ¿Salir de todos modos?');

    submit().click();
    await render();
    http.expectOne(API).flush({ ...PROFILE, fullName: 'Otro' });
    await render();
    confirm.mockClear();
    expect(leave()).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
    confirm.mockRestore();
  });
});
