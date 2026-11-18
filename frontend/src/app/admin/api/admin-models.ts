import { LocalizedText, OptionalLocalizedText, SkillCategory } from '../../core/models/profile';

/** A contact message as the admin inbox API returns it. */
export interface AdminMessage {
  id: number;
  name: string;
  email: string;
  message: string;
  locale: 'es' | 'en';
  createdAt: string;
  read: boolean;
}

export interface MessagePage {
  items: AdminMessage[];
  total: number;
  unread: number;
}

/** Whether "Publicar" is configured, and when the site was last asked to rebuild. */
export interface PublishStatus {
  configured: boolean;
  lastRequestedAt: string | null;
}

/** Body of `PUT /api/admin/profile`; empty optional fields are sent as null. */
export interface ProfileUpdate {
  fullName: string;
  headline: LocalizedText;
  about: LocalizedText;
  location: string | null;
  email: string | null;
  githubUrl: string | null;
  linkedinUrl: string | null;
  avatarUrl: string | null;
  cvUrl: OptionalLocalizedText;
}

/** A skill as the admin skills API returns it (with its position). */
export interface AdminSkill {
  id: number;
  name: string;
  category: SkillCategory;
  displayOrder: number;
}

/** Body of `PUT /api/admin/projects/{id}`; empty optional fields are sent as null. */
export interface ProjectUpdate {
  title: LocalizedText;
  summary: LocalizedText;
  description: LocalizedText;
  technologies: string[];
  repoUrl: string | null;
  demoUrl: string | null;
  featured: boolean;
  published: boolean;
}

/** Body of `POST /api/admin/projects`: the update fields plus the slug (fixed once created). */
export type ProjectCreate = ProjectUpdate & { slug: string };

/** A project as the admin projects API returns it (every field, drafts included). */
export type AdminProject = ProjectCreate & {
  id: number;
  imageUrl: string | null;
  displayOrder: number;
};
