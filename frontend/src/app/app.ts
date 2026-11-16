import { DOCUMENT, Location } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { SiteFooter } from './core/layout/site-footer/site-footer';
import { SiteHeader } from './core/layout/site-header/site-header';

@Component({
  imports: [RouterOutlet, SiteHeader, SiteFooter],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly document = inject(DOCUMENT);

  /** The admin area brings its own frame, so the public header and footer step aside. */
  protected readonly isAdmin = toSignal(
    inject(Router).events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => isAdminUrl(event.urlAfterRedirects)),
    ),
    { initialValue: isAdminUrl(inject(Location).path()) },
  );

  /** Moves keyboard focus to the page's main content without navigating. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    const main = this.document.getElementById('content');
    if (main) {
      main.setAttribute('tabindex', '-1');
      main.focus();
    }
  }
}

function isAdminUrl(url: string): boolean {
  return url === '/admin' || url.startsWith('/admin/') || url.startsWith('/admin?');
}
