import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Component, LOCALE_ID, computed, effect, inject, input } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ProjectDetail } from '../../core/models/project';
import { SeoService } from '../../core/seo/seo.service';
import { Icon } from '../../shared/icon';
import { LocalizedPipe, pickLocalized } from '../../shared/localized.pipe';
import { splitParagraphs } from '../../shared/paragraphs';

@Component({
  selector: 'app-project-page',
  imports: [LocalizedPipe, Icon],
  templateUrl: './project-page.html',
  styleUrl: './project-page.scss',
})
export class ProjectPage {
  /** Bound from the `:slug` route parameter. */
  readonly slug = input.required<string>();

  private readonly locale = inject(LOCALE_ID);
  private readonly title = inject(Title);
  private readonly seo = inject(SeoService);

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
        const project = this.project.value();
        this.seo.setPage({
          title: `${pickLocalized(project.title, this.locale)} | ${siteTitle}`,
          description: pickLocalized(project.summary, this.locale),
          // Trailing slash: the prerendered page lives at projects/<slug>/index.html.
          path: `/projects/${project.slug}/`,
          type: 'article',
        });
      } else if (this.project.error()) {
        // The route has no static title; without this the tab keeps index.html's English one.
        this.title.setTitle(siteTitle);
      }
    });
  }
}
