/** A required text in both site languages. */
export interface LocalizedText {
  es: string;
  en: string;
}

/** An optional text in both site languages; either value may be missing. */
export interface OptionalLocalizedText {
  es: string | null;
  en: string | null;
}

export type SkillCategory = 'BACKEND' | 'FRONTEND' | 'DATABASE' | 'DEVOPS' | 'TOOLS';

export interface Skill {
  id: number;
  name: string;
  category: SkillCategory;
}

/** Shape of `GET /api/profile`. */
export interface Profile {
  fullName: string;
  headline: LocalizedText;
  about: LocalizedText;
  location: string | null;
  email: string | null;
  githubUrl: string | null;
  linkedinUrl: string | null;
  avatarUrl: string | null;
  cvUrl: OptionalLocalizedText;
  skills: Skill[];
}
