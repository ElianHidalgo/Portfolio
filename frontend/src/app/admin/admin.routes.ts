import { Routes } from '@angular/router';
import { AdminLayout } from './admin-layout/admin-layout';
import { adminGuard } from './auth/admin.guard';
import { AdminHomePage } from './home/admin-home-page';
import { LoginPage } from './login/login-page';

/** The admin area (Spanish only, rendered in the browser, loaded on demand). */
const adminRoutes: Routes = [
  { path: 'login', component: LoginPage, title: 'Iniciar sesión | Portafolio' },
  {
    path: '',
    component: AdminLayout,
    canActivate: [adminGuard],
    children: [{ path: '', component: AdminHomePage, title: 'Panel | Portafolio' }],
  },
];

export default adminRoutes;
