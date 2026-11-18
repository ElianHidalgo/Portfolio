import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Profile } from '../../core/models/profile';
import { AdminApi } from '../api/admin-api';
import { ProfileUpdate } from '../api/admin-models';
import { HasUnsavedChanges } from '../unsaved-changes.guard';

const HEADLINE_MAX = 160;
const ABOUT_MAX = 2500;
const PATH = /^[A-Za-z0-9][A-Za-z0-9._/-]*$/;

function isHttps(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname !== '';
  } catch {
    return false;
  }
}

/** An https link; empty is fine. */
const httpsLink: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  return value === '' || isHttps(value) ? null : { link: true };
};

/** An https link or a site-relative path without ".."; empty is fine. */
const linkOrPath: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (value === '') return null;
  if (PATH.test(value)) return value.includes('..') ? { path: true } : null;
  return isHttps(value) ? null : { path: true };
};

/** Required, and not just whitespace (the server trims, so it would reject it). */
const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  String(control.value ?? '').trim() === '' ? { required: true } : null;

const text = (max: number, required = false, ...extra: ValidatorFn[]) =>
  new FormControl('', {
    nonNullable: true,
    validators: [...(required ? [notBlank] : []), Validators.maxLength(max), ...extra],
  });

/** Edits the profile (name, headline, about, contact and links) in both site languages. */
@Component({
  selector: 'app-profile-page',
  imports: [ReactiveFormsModule],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.scss',
})
export class ProfilePage implements HasUnsavedChanges {
  private readonly api = inject(AdminApi);

  protected readonly headlineMax = HEADLINE_MAX;
  protected readonly aboutMax = ABOUT_MAX;
  protected readonly loaded = signal(false);
  protected readonly loadFailed = signal(false);
  protected readonly sending = signal(false);
  protected readonly status = signal<'idle' | 'saved' | 'invalid' | 'tooLarge' | 'error'>('idle');
  /** The field message of a 400 problem from the server, shown under the form. */
  protected readonly serverDetail = signal('');

  protected readonly form = new FormGroup({
    fullName: text(100, true),
    headlineEs: text(HEADLINE_MAX, true),
    headlineEn: text(HEADLINE_MAX, true),
    aboutEs: text(ABOUT_MAX, true),
    aboutEn: text(ABOUT_MAX, true),
    location: text(100),
    email: text(254, false, Validators.email),
    githubUrl: text(500, false, httpsLink),
    linkedinUrl: text(500, false, httpsLink),
    avatarUrl: text(500, false, linkOrPath),
    cvUrlEs: text(500, false, linkOrPath),
    cvUrlEn: text(500, false, linkOrPath),
  });

  constructor() {
    this.api.profile().subscribe({
      next: (profile) => {
        this.fill(profile);
        this.loaded.set(true);
      },
      error: () => this.loadFailed.set(true),
    });
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.form.dirty) event.preventDefault();
  }

  protected invalid(name: keyof ProfilePage['form']['controls']): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected length(name: 'headlineEs' | 'headlineEn' | 'aboutEs' | 'aboutEn'): number {
    return this.form.controls[name].value.length;
  }

  protected save(): void {
    if (this.sending()) return;
    this.form.markAllAsTouched();
    this.serverDetail.set('');
    if (this.form.invalid) {
      this.status.set('invalid');
      return;
    }
    this.sending.set(true);
    this.status.set('idle');
    this.api.saveProfile(this.body()).subscribe({
      next: (profile) => {
        this.fill(profile);
        this.sending.set(false);
        this.status.set('saved');
      },
      error: (error: unknown) => {
        this.sending.set(false);
        const failure = error instanceof HttpErrorResponse ? error : null;
        if (failure?.status === 400) {
          const detail = failure.error?.detail;
          this.serverDetail.set(typeof detail === 'string' ? detail : '');
          this.status.set('invalid');
        } else {
          this.status.set(failure?.status === 413 ? 'tooLarge' : 'error');
        }
      },
    });
  }

  /** Puts a profile into the form and marks it as saved (pristine). */
  private fill(profile: Profile): void {
    this.form.reset({
      fullName: profile.fullName,
      headlineEs: profile.headline.es,
      headlineEn: profile.headline.en,
      aboutEs: profile.about.es,
      aboutEn: profile.about.en,
      location: profile.location ?? '',
      email: profile.email ?? '',
      githubUrl: profile.githubUrl ?? '',
      linkedinUrl: profile.linkedinUrl ?? '',
      avatarUrl: profile.avatarUrl ?? '',
      cvUrlEs: profile.cvUrl.es ?? '',
      cvUrlEn: profile.cvUrl.en ?? '',
    });
  }

  private body(): ProfileUpdate {
    const v = this.form.getRawValue();
    const optional = (value: string) => value.trim() || null;
    return {
      fullName: v.fullName.trim(),
      headline: { es: v.headlineEs.trim(), en: v.headlineEn.trim() },
      about: { es: v.aboutEs.trim(), en: v.aboutEn.trim() },
      location: optional(v.location),
      email: optional(v.email),
      githubUrl: optional(v.githubUrl),
      linkedinUrl: optional(v.linkedinUrl),
      avatarUrl: optional(v.avatarUrl),
      cvUrl: { es: optional(v.cvUrlEs), en: optional(v.cvUrlEn) },
    };
  }
}
