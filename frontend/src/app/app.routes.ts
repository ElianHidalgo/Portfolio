import { Routes } from '@angular/router';
import { HomePage } from './public/home/home-page';
import { NotFoundPage } from './public/not-found/not-found-page';
import { ProjectPage } from './public/project/project-page';

export const routes: Routes = [
  { path: '', component: HomePage, title: $localize`:@@pageTitle:Portfolio` },
  // The page sets its own title once the project has loaded.
  { path: 'projects/:slug', component: ProjectPage },
  // Must stay last: future routes (e.g. the admin area) go above it.
  { path: '**', component: NotFoundPage },
];
