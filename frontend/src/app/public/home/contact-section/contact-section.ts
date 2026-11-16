import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, LOCALE_ID, afterNextRender, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs';
import { BackendWake } from '../../../core/http/backend-wake';
import { SLOW_AFTER_MS } from '../../../core/http/cold-start';
import { ContactApi } from './contact-api';

export type ContactState = 'idle' | 'sending' | 'sent' | 'limited' | 'error';

/** Length of the trimmed text: the API trims before validating, so the limits must match. */
function trimmedLength(min: number, max: number): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = control.value.trim().length;
    return length < min || length > max ? { trimmedLength: { min, max } } : null;
  };
}

@Component({
  selector: 'app-contact-section',
  imports: [ReactiveFormsModule],
  templateUrl: './contact-section.html',
  styleUrl: './contact-section.scss',
})
export class ContactSection {
  // Limits mirror the API's (ContactRequest).
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [trimmedLength(1, 100)] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    message: new FormControl('', { nonNullable: true, validators: [trimmedLength(10, 2000)] }),
    website: new FormControl('', { nonNullable: true }),
  });
  protected readonly state = signal<ContactState>('idle');
  protected readonly slow = signal(false);
  protected readonly submitted = signal(false);

  private readonly api = inject(ContactApi);
  private readonly wake = inject(BackendWake);
  private readonly locale = inject(LOCALE_ID).startsWith('es') ? 'es' : 'en';

  constructor() {
    const host: HTMLElement = inject(ElementRef).nativeElement;
    const destroyRef = inject(DestroyRef);
    // Wake the backend while the visitor scrolls towards the form (browser only).
    afterNextRender(() => {
      if (typeof IntersectionObserver === 'undefined') return;
      const observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((entry) => entry.isIntersecting)) {
            this.wake.wake();
            observer.disconnect();
          }
        },
        { rootMargin: '800px 0px' },
      );
      observer.observe(host);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  protected wakeBackend(): void {
    this.wake.wake();
  }

  protected showError(name: 'name' | 'email' | 'message'): boolean {
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
    this.api
      .send({ ...this.form.getRawValue(), locale: this.locale })
      .pipe(
        finalize(() => {
          clearTimeout(slowTimer);
          this.slow.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.state.set('sent');
          this.submitted.set(false);
          this.form.reset();
        },
        error: (error: unknown) => {
          this.state.set(error instanceof HttpErrorResponse && error.status === 429 ? 'limited' : 'error');
        },
      });
  }
}
