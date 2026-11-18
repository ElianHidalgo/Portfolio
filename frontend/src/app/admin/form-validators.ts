import { AbstractControl, FormControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/** True for an absolute https URL with a host. */
export function isHttps(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname !== '';
  } catch {
    return false;
  }
}

/** An https link; empty is fine. */
export const httpsLink: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  return value === '' || isHttps(value) ? null : { link: true };
};

/** Required, and not just whitespace (the server trims, so it would reject it). */
export const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  String(control.value ?? '').trim() === '' ? { required: true } : null;

/** A text control with a maximum length, optionally required, plus any extra validators. */
export const text = (max: number, required = false, ...extra: ValidatorFn[]) =>
  new FormControl('', {
    nonNullable: true,
    validators: [...(required ? [notBlank] : []), Validators.maxLength(max), ...extra],
  });
