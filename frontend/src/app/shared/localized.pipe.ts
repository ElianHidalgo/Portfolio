import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { OptionalLocalizedText } from '../core/models/profile';

/** Picks the value for a locale: Spanish for any `es*` locale, English otherwise; `''` when missing. */
export function pickLocalized(text: OptionalLocalizedText | null, locale: string): string {
  return text?.[locale.startsWith('es') ? 'es' : 'en'] ?? '';
}

/** Template version of `pickLocalized` for the language of the current build (`/es/` or `/en/`). */
@Pipe({ name: 'localized' })
export class LocalizedPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(text: OptionalLocalizedText | null): string {
    return pickLocalized(text, this.locale);
  }
}
