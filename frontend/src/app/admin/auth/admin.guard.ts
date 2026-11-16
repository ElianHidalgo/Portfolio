import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminSession } from './admin-session';

/** The panel needs a signed-in admin; anyone else goes to the login page. */
export const adminGuard: CanActivateFn = () =>
  inject(AdminSession).isAuthenticated() || inject(Router).parseUrl('/admin/login');
