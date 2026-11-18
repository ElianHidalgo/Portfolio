import { Routes } from '@angular/router';
import { AdminLayout } from './admin-layout/admin-layout';
import { adminGuard } from './auth/admin.guard';
import { AdminHomePage } from './home/admin-home-page';
import { LoginPage } from './login/login-page';
import { MessagesPage } from './messages/messages-page';

/** The admin area (Spanish only, rendered in the browser, loaded on demand). */
const adminRoutes: Routes = [
  { path: 'login', component: LoginPage, title: 'Iniciar sesión | Portafolio' },
  {
    path: '',
    component: AdminLayout,
    canActivate: [adminGuard],
    children: [
      { path: '', component: AdminHomePage, title: 'Panel | Portafolio' },
      { path: 'mensajes', component: MessagesPage, title: 'Mensajes | Portafolio' },
    ],
  },
];

export default adminRoutes;
