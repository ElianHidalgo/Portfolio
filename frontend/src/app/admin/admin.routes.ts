import { Routes } from '@angular/router';
import { AdminLayout } from './admin-layout/admin-layout';
import { adminGuard } from './auth/admin.guard';
import { AdminHomePage } from './home/admin-home-page';
import { LoginPage } from './login/login-page';
import { MessagesPage } from './messages/messages-page';
import { ProfilePage } from './profile/profile-page';
import { ProjectEditPage } from './projects/project-edit-page';
import { ProjectsPage } from './projects/projects-page';
import { SkillsPage } from './skills/skills-page';
import { unsavedChangesGuard } from './unsaved-changes.guard';

/** The admin area (Spanish only, rendered in the browser, loaded on demand). */
const adminRoutes: Routes = [
  { path: 'login', component: LoginPage, title: 'Iniciar sesión | Portafolio' },
  {
    path: '',
    component: AdminLayout,
    canActivate: [adminGuard],
    children: [
      { path: '', component: AdminHomePage, title: 'Panel | Portafolio' },
      { path: 'perfil', component: ProfilePage, title: 'Perfil | Portafolio', canDeactivate: [unsavedChangesGuard] },
      { path: 'skills', component: SkillsPage, title: 'Skills | Portafolio' },
      { path: 'proyectos', component: ProjectsPage, title: 'Proyectos | Portafolio' },
      { path: 'proyectos/editar', component: ProjectEditPage, title: 'Proyecto | Portafolio', canDeactivate: [unsavedChangesGuard] },
      { path: 'mensajes', component: MessagesPage, title: 'Mensajes | Portafolio' },
    ],
  },
];

export default adminRoutes;
