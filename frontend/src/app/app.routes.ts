import { Routes } from '@angular/router';
import { HomePage } from './public/home/home-page';
import { ProjectPage } from './public/project/project-page';

export const routes: Routes = [
  { path: '', component: HomePage, title: $localize`:@@pageTitle:Portfolio` },
  // The page sets its own title once the project has loaded.
  { path: 'projects/:slug', component: ProjectPage },
];
