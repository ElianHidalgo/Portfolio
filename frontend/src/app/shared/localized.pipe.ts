import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { OptionalLocalizedText } from '../core/models/profile';

/** Picks the value for the language of the current build (`/es/` or `/en/`). */
@Pipe({ name: 'localized' })
export class LocalizedPipe implements PipeTransform {
  private readonly language = inject(LOCALE_ID).startsWith('es') ? 'es' : 'en';

  transform(text: OptionalLocalizedText | null): string {
    return text?.[this.language] ?? '';
  }
}
