import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { adminGuard } from './admin.guard';
import { fakeJwt } from './fake-jwt.spec-helper';

describe('adminGuard', () => {
  const run = () =>
    TestBed.runInInjectionContext(() => adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  afterEach(() => sessionStorage.clear());

  it('lets a signed-in admin through and sends others to the login', () => {
    sessionStorage.setItem('portfolio.admin.token', fakeJwt(3600));
    expect(run()).toBe(true);

    sessionStorage.setItem('portfolio.admin.token', fakeJwt(-10));
    const result = run();
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/admin/login');
  });
});
