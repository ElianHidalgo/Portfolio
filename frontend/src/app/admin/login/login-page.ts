import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { BackendWake } from '../../core/http/backend-wake';
import { SLOW_AFTER_MS } from '../../core/http/cold-start';
import { AdminSession } from '../auth/admin-session';

export type LoginState = 'idle' | 'sending' | 'invalid' | 'limited' | 'error';

/** Admin sign-in (Spanish only). Opening it wakes the backend, which may be asleep. */
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly state = signal<LoginState>('idle');
  protected readonly slow = signal(false);
  protected readonly submitted = signal(false);

  private readonly session = inject(AdminSession);
  private readonly router = inject(Router);
  protected readonly expired = this.session.lastLogoutReason() === 'expired';

  constructor() {
    inject(BackendWake).wake();
    if (this.session.isAuthenticated()) {
      void this.router.navigateByUrl('/admin');
    }
  }

  protected showError(name: 'username' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected submit(): void {
    if (this.state() === 'sending') return;
    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state.set('sending');
    const slowTimer = setTimeout(() => this.slow.set(true), SLOW_AFTER_MS);
    const { username, password } = this.form.getRawValue();
    this.session
      .login(username, password)
      .pipe(
        finalize(() => {
          clearTimeout(slowTimer);
          this.slow.set(false);
        }),
      )
      .subscribe({
        next: () => void this.router.navigateByUrl('/admin'),
        error: (error: unknown) => {
          const status = error instanceof HttpErrorResponse ? error.status : 0;
          if (status === 401) {
            this.state.set('invalid');
            this.form.controls.password.reset();
          } else {
            this.state.set(status === 429 ? 'limited' : 'error');
          }
        },
      });
  }
}
