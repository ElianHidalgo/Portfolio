import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';

@Component({ template: '<main id="content">Page</main>' })
class PageStub {}

describe('App shell', () => {
  it('skip link moves focus to the main content without leaving the page', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', component: PageStub }]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/projects/gamevault');
    await fixture.whenStable();

    const skip = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('a.skip-link')!;
    const click = new MouseEvent('click', { bubbles: true, cancelable: true });
    skip.dispatchEvent(click);

    expect(click.defaultPrevented).toBe(true);
    expect(document.activeElement?.id).toBe('content');
    expect(TestBed.inject(Router).url).toBe('/projects/gamevault');
  });
});

describe('App routes', () => {
  it("navigating to '' renders the home page", async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/');

    expect(harness.routeNativeElement?.tagName.toLowerCase()).toBe('app-home-page');
  });

  it('any unknown path renders the not-found page with a way home', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/admin/whatever');

    const page = harness.routeNativeElement!;
    expect(page.tagName.toLowerCase()).toBe('app-not-found-page');
    expect(page.querySelector('[data-state="not-found"]')).not.toBeNull();
    expect(page.querySelector('a')?.getAttribute('href')).toBe('./');
  });

  it("navigating to '/projects/:slug' renders the project page", async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/projects/gamevault');

    expect(harness.routeNativeElement?.tagName.toLowerCase()).toBe('app-project-page');
  });
});
