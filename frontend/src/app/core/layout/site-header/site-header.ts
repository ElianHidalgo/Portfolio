import { Component, LOCALE_ID, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';

@Component({
  selector: 'app-site-header',
  imports: [RouterLink],
  templateUrl: './site-header.html',
  styleUrl: './site-header.scss',
})
export class SiteHeader {
  private readonly router = inject(Router);

  protected readonly isSpanish = inject(LOCALE_ID).startsWith('es');

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** Same page in the other language: each locale is served under its own prefix (`/es/`, `/en/`). */
  protected readonly otherLanguageHref = computed(
    () => `/${this.isSpanish ? 'en' : 'es'}${this.url().split('#')[0]}`,
  );
}
