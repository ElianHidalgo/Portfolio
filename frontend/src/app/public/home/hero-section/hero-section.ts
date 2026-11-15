import { Component, input } from '@angular/core';
import { Profile } from '../../../core/models/profile';
import { Icon } from '../../../shared/icon';
import { LocalizedPipe } from '../../../shared/localized.pipe';

@Component({
  selector: 'app-hero-section',
  imports: [LocalizedPipe, Icon],
  templateUrl: './hero-section.html',
  styleUrl: './hero-section.scss',
})
export class HeroSection {
  readonly profile = input.required<Profile>();
}
