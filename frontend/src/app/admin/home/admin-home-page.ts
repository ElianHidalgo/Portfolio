import { httpResource } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { API_ORIGIN } from '../../core/http/api-origin';

interface AdminSessionInfo {
  username: string;
  expiresAt: string;
}

/** The panel's landing page. Part 8 adds profile, projects and messages here. */
@Component({
  selector: 'app-admin-home-page',
  template: `
    <h1 class="admin-page__title">Panel de administración</h1>
    @if (session.hasValue()) {
      <p class="admin-page__text">Sesión iniciada como {{ session.value().username }}</p>
    } @else if (session.error()) {
      <p class="admin-page__text">No se pudo comprobar la sesión. Recarga la página.</p>
    } @else {
      <p class="admin-page__text">Comprobando la sesión…</p>
    }
  `,
  styles: `
    .admin-page__title {
      margin: 0 0 var(--space-s);
      font-size: var(--text-xl);
      font-weight: 600;
      letter-spacing: -0.015em;
    }
    .admin-page__text {
      margin: 0;
      color: var(--color-muted);
    }
  `,
})
export class AdminHomePage {
  private readonly apiOrigin = inject(API_ORIGIN);
  protected readonly session = httpResource<AdminSessionInfo>(() => `${this.apiOrigin}/api/admin/session`);
}
