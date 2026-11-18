import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminSession } from './admin-session';

/**
 * The panel needs a signed-in admin; anyone else goes to the login page. A token that has
 * run out is cleared and reported as expired, so the login page can say why.
 */
export const adminGuard: CanActivateFn = () => {
  const session = inject(AdminSession);
  if (session.isAuthenticated()) {
    return true;
  }
  if (session.token()) {
    session.logout('expired');
  }
  return inject(Router).parseUrl('/admin/login');
};
