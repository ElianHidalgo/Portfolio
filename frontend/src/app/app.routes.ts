import { LOCALE_ID, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { HomePage } from './public/home/home-page';
import { NotFoundPage } from './public/not-found/not-found-page';
import { ProjectPage } from './public/project/project-page';

export const routes: Routes = [
  { path: '', component: HomePage, title: $localize`:@@pageTitle:Portfolio` },
  // The page sets its own title once the project has loaded.
  { path: 'projects/:slug', component: ProjectPage },
  // Admin area: Spanish build only (spec), loaded on demand so the public site does not carry it.
  {
    path: 'admin',
    canMatch: [() => inject(LOCALE_ID).startsWith('es')],
    loadChildren: () => import('./admin/admin.routes'),
  },
  // Must stay last.
  { path: '**', component: NotFoundPage },
];
