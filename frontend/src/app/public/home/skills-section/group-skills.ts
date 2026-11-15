import { Skill, SkillCategory } from '../../../core/models/profile';

export interface SkillGroup {
  category: SkillCategory;
  label: string;
  skills: Skill[];
}

/** Display order and translated label of each category. */
const CATEGORIES: ReadonlyArray<{ category: SkillCategory; label: string }> = [
  { category: 'BACKEND', label: $localize`:@@skillCategory.BACKEND:Backend` },
  { category: 'FRONTEND', label: $localize`:@@skillCategory.FRONTEND:Frontend` },
  { category: 'DATABASE', label: $localize`:@@skillCategory.DATABASE:Databases` },
  { category: 'DEVOPS', label: $localize`:@@skillCategory.DEVOPS:DevOps` },
  { category: 'TOOLS', label: $localize`:@@skillCategory.TOOLS:Tools` },
];

/** Groups skills by category in the fixed order, keeping input order inside each group. */
export function groupSkills(skills: Skill[]): SkillGroup[] {
  return CATEGORIES.map(({ category, label }) => ({
    category,
    label,
    skills: skills.filter((skill) => skill.category === category),
  })).filter((group) => group.skills.length > 0);
}
