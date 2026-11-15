import { Component, computed, inject, input } from '@angular/core';
import { Profile } from '../../../core/models/profile';
import { LocalizedPipe } from '../../../shared/localized.pipe';

@Component({
  selector: 'app-about-section',
  providers: [LocalizedPipe],
  templateUrl: './about-section.html',
  styleUrl: './about-section.scss',
})
export class AboutSection {
  readonly profile = input.required<Profile>();

  private readonly localized = inject(LocalizedPipe);

  /** The about text split on blank lines, one entry per paragraph. */
  protected readonly paragraphs = computed(() =>
    this.localized
      .transform(this.profile().about)
      .split(/\n\s*\n/)
      .map((paragraph) => paragraph.trim())
      .filter((paragraph) => paragraph.length > 0),
  );
}
