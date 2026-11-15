import { httpResource } from '@angular/common/http';
import { Component } from '@angular/core';
import { ProjectSummary } from '../../../core/models/project';
import { scrollToFragmentWhenReady } from '../../../shared/fragment-scroll';
import { Icon } from '../../../shared/icon';
import { LocalizedPipe } from '../../../shared/localized.pipe';

/** Loads its own data so a projects failure never hides the rest of the home page. */
@Component({
  selector: 'app-projects-section',
  imports: [LocalizedPipe, Icon],
  templateUrl: './projects-section.html',
  styleUrl: './projects-section.scss',
})
export class ProjectsSection {
  protected readonly projects = httpResource<ProjectSummary[]>(() => '/api/projects');

  constructor() {
    // Sections at or below this one move once the projects render (or fail), so they wait for it.
    scrollToFragmentWhenReady(() => !this.projects.isLoading(), ['projects', 'skills']);
  }
}
