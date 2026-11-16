import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { API_ORIGIN } from '../../../core/http/api-origin';
import { BackendWake } from '../../../core/http/backend-wake';
import { ContactSection } from './contact-section';

const URL = 'https://api.example/api/contact';

describe('ContactSection', () => {
  let fixture: ComponentFixture<ContactSection>;
  let http: HttpTestingController;
  const wake = { wake: vi.fn() };

  const el = () => fixture.nativeElement as HTMLElement;
  const section = () => el().querySelector('#contact') as HTMLElement;
  const field = (name: string) => el().querySelector(`[name="${name}"]`) as HTMLInputElement | HTMLTextAreaElement;
  const errors = () => Array.from(el().querySelectorAll('.contact__error')).map((n) => n.textContent?.trim());
  const sendButton = () => el().querySelector('button[type="submit"]') as HTMLButtonElement;

  function type(name: string, value: string): void {
    const input = field(name);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillValid(): void {
    type('name', 'Ana');
    type('email', 'ana@example.com');
    type('message', 'Hello, I have a project.');
  }

  async function submit(): Promise<void> {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    wake.wake.mockReset();
    TestBed.configureTestingModule({
      imports: [ContactSection],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.example' },
        { provide: BackendWake, useValue: wake },
      ],
    });
    fixture = TestBed.createComponent(ContactSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => vi.useRealTimers());

  it('does not send an invalid form and explains each field', async () => {
    await submit();

    http.expectNone(URL);
    expect(errors()).toEqual(['Enter your name.', 'Enter a valid email.', 'Write between 10 and 2,000 characters.']);
    expect(field('email').getAttribute('aria-invalid')).toBe('true');
  });

  it('rejects a message made only of spaces', async () => {
    type('name', 'Ana');
    type('email', 'ana@example.com');
    type('message', '            ');
    await submit();

    http.expectNone(URL);
    expect(errors()).toEqual(['Write between 10 and 2,000 characters.']);
  });

  it('sends the message with the page language and shows success', async () => {
    fillValid();
    await submit();

    const request = http.expectOne(URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'Ana', email: 'ana@example.com', message: 'Hello, I have a project.', locale: 'en', website: '',
    });
    expect(section().dataset['formState']).toBe('sending');
    expect(sendButton().disabled).toBe(true);

    request.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(section().dataset['formState']).toBe('sent');
    expect(el().textContent).toContain('Thanks! Your message was sent.');
    expect(field('name').value).toBe('');
  });

  it('shows the limit message on 429 and keeps the input', async () => {
    fillValid();
    await submit();
    http.expectOne(URL).flush({ status: 429 }, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(section().dataset['formState']).toBe('limited');
    expect(el().textContent).toContain('Too many messages for now. Try again later.');
    expect(field('message').value).toBe('Hello, I have a project.');
  });

  it('shows the waking hint while the server wakes up, then a retryable error on timeout', async () => {
    vi.useFakeTimers();
    fillValid();
    await submit();
    const request = http.expectOne(URL);

    vi.advanceTimersByTime(5_000);
    fixture.detectChanges();
    expect(el().textContent).toContain('The server is waking up; this can take up to two minutes.');

    // Render can take ~2 minutes to wake: the request must still be alive at 90 s.
    vi.advanceTimersByTime(85_000);
    fixture.detectChanges();
    expect(request.cancelled).toBe(false);
    expect(section().dataset['formState']).toBe('sending');

    vi.advanceTimersByTime(40_000);
    fixture.detectChanges();
    expect(request.cancelled).toBe(true);
    expect(section().dataset['formState']).toBe('error');
    expect(el().textContent).toContain('The message couldn’t be sent. Try again.');
    expect(el().textContent).not.toContain('The server is waking up');
    expect(field('message').value).toBe('Hello, I have a project.');
    expect(sendButton().disabled).toBe(false);
  });

  it('wakes the backend when the visitor starts using the form', () => {
    el().querySelector('form')!.dispatchEvent(new FocusEvent('focusin'));

    expect(wake.wake).toHaveBeenCalled();
  });

  it('hides the honeypot from people and assistive technology', () => {
    const trap = field('website');
    expect(trap.tabIndex).toBe(-1);
    expect(trap.getAttribute('autocomplete')).toBe('off');
    expect(trap.closest('[aria-hidden="true"]')).not.toBeNull();
  });

  it('never renders a data-state attribute (the prerender guard would reject the page)', () => {
    expect(el().innerHTML).not.toContain('data-state=');
  });
});
