import { Component, LOCALE_ID, computed, inject, input } from '@angular/core';
import { Profile } from '../../../core/models/profile';
import { pickLocalized } from '../../../shared/localized.pipe';
import { splitParagraphs } from '../../../shared/paragraphs';

@Component({
  selector: 'app-about-section',
  templateUrl: './about-section.html',
  styleUrl: './about-section.scss',
})
export class AboutSection {
  readonly profile = input.required<Profile>();

  private readonly locale = inject(LOCALE_ID);

  protected readonly paragraphs = computed(() =>
    splitParagraphs(pickLocalized(this.profile().about, this.locale)),
  );
}
