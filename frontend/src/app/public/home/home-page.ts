import { httpResource } from '@angular/common/http';
import { Component } from '@angular/core';
import { Profile } from '../../core/models/profile';
import { scrollToFragmentWhenReady } from '../../shared/fragment-scroll';
import { AboutSection } from './about-section/about-section';
import { HeroSection } from './hero-section/hero-section';
import { ProjectsSection } from './projects-section/projects-section';
import { SkillsSection } from './skills-section/skills-section';

@Component({
  selector: 'app-home-page',
  imports: [HeroSection, AboutSection, ProjectsSection, SkillsSection],
  templateUrl: './home-page.html',
  styleUrl: './home-page.scss',
})
export class HomePage {
  protected readonly profile = httpResource<Profile>(() => '/api/profile');

  constructor() {
    scrollToFragmentWhenReady(() => this.profile.hasValue(), ['about', 'skills']);
  }
}
