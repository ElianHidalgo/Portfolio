import { DOCUMENT } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
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
