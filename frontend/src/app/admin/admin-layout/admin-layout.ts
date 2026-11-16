import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AdminSession } from '../auth/admin-session';

/** Frame of the signed-in admin area: its own header instead of the public site's. */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
  private readonly session = inject(AdminSession);
  private readonly router = inject(Router);

  protected logout(): void {
    this.session.logout();
    void this.router.navigateByUrl('/admin/login');
  }
}
