import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_ORIGIN } from '../../core/http/api-origin';
import { AdminSession } from './admin-session';

/**
 * Sends the admin token only to the admin API on our own API origin — never to public
 * endpoints or other sites. When the API refuses it (expired), the admin signs in again.
 */
export const adminAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const adminPrefix = `${inject(API_ORIGIN)}/api/admin/`;
  if (!request.url.startsWith(adminPrefix)) {
    return next(request);
  }
  const session = inject(AdminSession);
  const router = inject(Router);
  const token = session.token();
  const authorized = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        session.logout('expired');
        void router.navigateByUrl('/admin/login');
      }
      return throwError(() => error);
    }),
  );
};
