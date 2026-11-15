import { LocalizedText } from './profile';

/** One item of `GET /api/projects`. */
export interface ProjectSummary {
  slug: string;
  title: LocalizedText;
  summary: LocalizedText;
  technologies: string[];
  repoUrl: string | null;
  demoUrl: string | null;
  imageUrl: string | null;
  featured: boolean;
}

/** Shape of `GET /api/projects/{slug}`. */
export interface ProjectDetail extends ProjectSummary {
  description: LocalizedText;
}
