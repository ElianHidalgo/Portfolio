import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';

/** Any path no other route matches. Rendered in the browser (static hosts send it with a 404). */
@Component({
  selector: 'app-not-found-page',
  template: `
    <main id="content" class="page">
      <div class="status" role="alert" data-state="not-found">
        <p i18n="@@pageNotFound">This page doesn’t exist.</p>
        <a class="button" href="./" i18n="@@pageNotFoundBack">Go to the home page</a>
      </div>
    </main>
  `,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class NotFoundPage {
  constructor() {
    inject(Title).setTitle($localize`:@@pageTitle:Portfolio`);
  }
}
