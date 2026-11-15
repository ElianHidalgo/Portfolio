import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Component, LOCALE_ID, computed, effect, inject, input } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ProjectDetail } from '../../core/models/project';
import { Icon } from '../../shared/icon';
import { LocalizedPipe, pickLocalized } from '../../shared/localized.pipe';
import { splitParagraphs } from '../../shared/paragraphs';

@Component({
  selector: 'app-project-page',
  imports: [RouterLink, LocalizedPipe, Icon],
  templateUrl: './project-page.html',
  styleUrl: './project-page.scss',
})
export class ProjectPage {
  /** Bound from the `:slug` route parameter. */
  readonly slug = input.required<string>();

  private readonly locale = inject(LOCALE_ID);
  private readonly title = inject(Title);

  protected readonly project = httpResource<ProjectDetail>(() => `/api/projects/${this.slug()}`);

  protected readonly notFound = computed(() => {
    const error = this.project.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });

  protected readonly paragraphs = computed(() =>
    this.project.hasValue() ? splitParagraphs(pickLocalized(this.project.value().description, this.locale)) : [],
  );

  constructor() {
    const siteTitle = $localize`:@@pageTitle:Portfolio`;
    effect(() => {
      if (this.project.hasValue()) {
        const name = pickLocalized(this.project.value().title, this.locale);
        this.title.setTitle(`${name} | ${siteTitle}`);
      } else if (this.project.error()) {
        // The route has no static title; without this the tab keeps index.html's English one.
        this.title.setTitle(siteTitle);
      }
    });
  }
}
