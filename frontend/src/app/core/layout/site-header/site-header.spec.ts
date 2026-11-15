import { Component, LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { SiteHeader } from './site-header';

@Component({ template: '' })
class Blank {}

describe('SiteHeader', () => {
  async function headerAt(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Blank },
          { path: 'projects/:slug', component: Blank },
        ]),
        { provide: LOCALE_ID, useValue: 'es' },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    const fixture = TestBed.createComponent(SiteHeader);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('keeps the current page when switching language', async () => {
    const header = await headerAt('/projects/gamevault');
    const lang = header.querySelector('a.lang-switch');
    expect(lang?.getAttribute('href')).toBe('/en/projects/gamevault');
  });

  it('links its sections to the home page', async () => {
    const header = await headerAt('/projects/gamevault');
    const links = Array.from(header.querySelectorAll('nav ul a'));
    expect(links.map((a) => a.textContent?.trim())).toEqual(['About', 'Projects', 'Skills']);
    expect(links[1].getAttribute('href')).toBe('/#projects');
  });
});
