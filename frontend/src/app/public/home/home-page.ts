import { httpResource } from '@angular/common/http';
import { Component, LOCALE_ID, inject } from '@angular/core';
import { Profile } from '../../core/models/profile';
import { AboutSection } from './about-section/about-section';
import { HeroSection } from './hero-section/hero-section';
import { SkillsSection } from './skills-section/skills-section';

/** Source code of this site, linked from the footer. */
const SOURCE_URL = 'https://github.com/ElianHidalgo/Portfolio';

@Component({
  selector: 'app-home-page',
  imports: [HeroSection, AboutSection, SkillsSection],
  templateUrl: './home-page.html',
  styleUrl: './home-page.scss',
})
export class HomePage {
  protected readonly profile = httpResource<Profile>(() => '/api/profile');

  protected readonly isSpanish = inject(LOCALE_ID).startsWith('es');
  protected readonly sourceUrl = SOURCE_URL;
  protected readonly year = new Date().getFullYear();
}
