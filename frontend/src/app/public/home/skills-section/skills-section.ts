import { Component, computed, input } from '@angular/core';
import { Profile } from '../../../core/models/profile';
import { groupSkills } from './group-skills';

@Component({
  selector: 'app-skills-section',
  templateUrl: './skills-section.html',
  styleUrl: './skills-section.scss',
})
export class SkillsSection {
  readonly profile = input.required<Profile>();

  protected readonly groups = computed(() => groupSkills(this.profile().skills));
}
