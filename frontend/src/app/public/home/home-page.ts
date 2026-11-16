import { httpResource } from '@angular/common/http';
import { Component, LOCALE_ID, effect, inject } from '@angular/core';
import { Profile } from '../../core/models/profile';
import { SeoService } from '../../core/seo/seo.service';
import { pickLocalized } from '../../shared/localized.pipe';
import { scrollToFragmentWhenReady } from '../../shared/fragment-scroll';
import { AboutSection } from './about-section/about-section';
import { ContactSection } from './contact-section/contact-section';
import { HeroSection } from './hero-section/hero-section';
import { ProjectsSection } from './projects-section/projects-section';
import { SkillsSection } from './skills-section/skills-section';

@Component({
  selector: 'app-home-page',
  imports: [HeroSection, AboutSection, ProjectsSection, SkillsSection, ContactSection],
  templateUrl: './home-page.html',
  styleUrl: './home-page.scss',
})
export class HomePage {
  protected readonly profile = httpResource<Profile>(() => '/api/profile');

  private readonly seo = inject(SeoService);
  private readonly locale = inject(LOCALE_ID);

  constructor() {
    effect(() => {
      if (this.profile.hasValue()) {
        const profile = this.profile.value();
        this.seo.setPage({
          title: `${profile.fullName} | ${$localize`:@@pageTitle:Portfolio`}`,
          description: pickLocalized(profile.headline, this.locale),
          path: '/',
        });
      }
    });
    // 'projects' and 'skills' are handled by ProjectsSection: they shift when the projects render.
    scrollToFragmentWhenReady(() => this.profile.hasValue(), ['about']);
  }
}
