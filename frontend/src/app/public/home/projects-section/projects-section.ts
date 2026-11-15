import { httpResource } from '@angular/common/http';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectSummary } from '../../../core/models/project';
import { scrollToFragmentWhenReady } from '../../../shared/fragment-scroll';
import { Icon } from '../../../shared/icon';
import { LocalizedPipe } from '../../../shared/localized.pipe';

/** Loads its own data so a projects failure never hides the rest of the home page. */
@Component({
  selector: 'app-projects-section',
  imports: [RouterLink, LocalizedPipe, Icon],
  templateUrl: './projects-section.html',
  styleUrl: './projects-section.scss',
})
export class ProjectsSection {
  protected readonly projects = httpResource<ProjectSummary[]>(() => '/api/projects');

  constructor() {
    scrollToFragmentWhenReady(() => this.projects.hasValue(), ['projects']);
  }
}
