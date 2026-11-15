import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('App routes', () => {
  it("navigating to '' renders the home page", async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/');

    expect(harness.routeNativeElement?.tagName.toLowerCase()).toBe('app-home-page');
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
