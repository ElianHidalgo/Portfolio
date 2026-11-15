import { Component, input } from '@angular/core';
import { Profile } from '../../../core/models/profile';
import { LocalizedPipe } from '../../../shared/localized.pipe';

@Component({
  selector: 'app-hero-section',
  imports: [LocalizedPipe],
  templateUrl: './hero-section.html',
  styleUrl: './hero-section.scss',
})
export class HeroSection {
  readonly profile = input.required<Profile>();
}
