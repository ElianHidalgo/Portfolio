import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AdminApi } from '../api/admin-api';
import { AdminProject, ProjectUpdate } from '../api/admin-models';
import { httpsLink, text } from '../form-validators';
import { HasUnsavedChanges } from '../unsaved-changes.guard';

const TITLE_MAX = 120;
const SUMMARY_MAX = 300;
const DESCRIPTION_MAX = 4000;
const TECHNOLOGIES_MAX = 20;
const TECHNOLOGY_MAX = 40;
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const CREATED_NOTICE = 'Proyecto creado. Está oculto hasta que marques Publicado.';

/** Splits a comma separated list, trims every entry and drops the empty ones. */
export function parseTechnologies(value: string): string[] {
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry !== '');
}

const slugPattern: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  SLUG.test(String(control.value ?? '').trim()) ? null : { slug: true };

/** At most 20 technologies of at most 40 characters, without case-insensitive repeats. */
const technologyList: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const items = parseTechnologies(String(control.value ?? ''));
  if (items.length > TECHNOLOGIES_MAX) return { count: true };
  if (items.some((item) => item.length > TECHNOLOGY_MAX)) return { length: true };
  const seen = new Set<string>();
  for (const item of items) {
    const key = item.toLowerCase();
    if (seen.has(key)) return { duplicate: item };
    seen.add(key);
  }
  return null;
};

/** Creates a project, or edits the one named by the `id` query parameter, in both site languages. */
@Component({
  selector: 'app-project-edit-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './project-edit-page.html',
  styleUrl: './project-edit-page.scss',
})
export class ProjectEditPage implements HasUnsavedChanges {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly id: number | null;
  /** The stored image link: the form has no field for it, so it is sent back unchanged. */
  private imageUrl: string | null = null;

  protected readonly titleMax = TITLE_MAX;
  protected readonly summaryMax = SUMMARY_MAX;
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly creating: boolean;
  protected readonly slug = signal('');
  protected readonly loaded = signal(false);
  protected readonly loadFailed = signal<'missing' | 'error' | null>(null);
  protected readonly sending = signal(false);
  protected readonly status = signal<'idle' | 'saved' | 'invalid' | 'tooLarge' | 'error'>('idle');
  /** The server answered 409: a repeated slug when creating, the last published project when updating. */
  protected readonly conflict = signal(false);
  protected readonly serverDetail = signal('');

  protected readonly form = new FormGroup({
    slug: text(80, true, slugPattern),
    titleEs: text(TITLE_MAX, true),
    titleEn: text(TITLE_MAX, true),
    summaryEs: text(SUMMARY_MAX, true),
    summaryEn: text(SUMMARY_MAX, true),
    descriptionEs: text(DESCRIPTION_MAX, true),
    descriptionEn: text(DESCRIPTION_MAX, true),
    technologies: text(2000, false, technologyList),
    repoUrl: text(500, false, httpsLink),
    demoUrl: text(500, false, httpsLink),
    featured: new FormControl(false, { nonNullable: true }),
    published: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    const raw = inject(ActivatedRoute).snapshot.queryParamMap.get('id');
    const id = raw !== null && /^[1-9]\d*$/.test(raw) ? Number(raw) : null;
    this.id = id;
    this.creating = raw === null;
    if (raw === null) {
      this.loaded.set(true);
      return;
    }
    if (id === null) {
      // A mistyped edit link must not turn into a create form.
      this.loadFailed.set('missing');
      return;
    }
    this.form.controls.slug.disable();
    this.api.project(id).subscribe({
      next: (project) => {
        this.fill(project);
        this.loaded.set(true);
      },
      error: (error: unknown) =>
        this.loadFailed.set(error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'error'),
    });
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.form.dirty) event.preventDefault();
  }

  protected invalid(name: keyof ProjectEditPage['form']['controls']): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected length(name: 'summaryEs' | 'summaryEn' | 'descriptionEs' | 'descriptionEn'): number {
    return this.form.controls[name].value.length;
  }

  /** The message of the technologies check that failed, or an empty string. */
  protected technologiesError(): string {
    const errors = this.form.controls.technologies.errors;
    if (!errors) return '';
    if (errors['duplicate']) return `Tecnología repetida: ${errors['duplicate']}`;
    if (errors['count']) return `Máximo ${TECHNOLOGIES_MAX} tecnologías.`;
    return `Cada tecnología admite como máximo ${TECHNOLOGY_MAX} caracteres.`;
  }

  protected save(): void {
    if (this.sending()) return;
    this.form.markAllAsTouched();
    this.serverDetail.set('');
    this.conflict.set(false);
    if (this.form.invalid) {
      this.status.set('invalid');
      return;
    }
    this.sending.set(true);
    this.status.set('idle');
    const body = this.body();
    const request =
      this.id === null
        ? this.api.createProject({ slug: this.form.controls.slug.value.trim(), ...body })
        : this.api.updateProject(this.id, body);
    request.subscribe({
      next: (project) => {
        this.sending.set(false);
        if (this.id === null) {
          // Pristine first, so the unsaved-changes guard does not ask while leaving.
          this.form.markAsPristine();
          void this.router.navigateByUrl('/admin/proyectos', { state: { notice: CREATED_NOTICE } });
        } else {
          this.fill(project);
          this.status.set('saved');
        }
      },
      error: (error: unknown) => {
        this.sending.set(false);
        const failure = error instanceof HttpErrorResponse ? error : null;
        if (failure?.status === 409) {
          this.conflict.set(true);
          this.status.set('idle');
        } else if (failure?.status === 400) {
          const detail = failure.error?.detail;
          this.serverDetail.set(typeof detail === 'string' ? detail : '');
          this.status.set('invalid');
        } else {
          this.status.set(failure?.status === 413 ? 'tooLarge' : 'error');
        }
      },
    });
  }

  /** Puts a project into the form and marks it as saved (pristine). */
  private fill(project: AdminProject): void {
    this.slug.set(project.slug);
    this.imageUrl = project.imageUrl;
    this.form.reset({
      slug: project.slug,
      titleEs: project.title.es,
      titleEn: project.title.en,
      summaryEs: project.summary.es,
      summaryEn: project.summary.en,
      descriptionEs: project.description.es,
      descriptionEn: project.description.en,
      technologies: project.technologies.join(', '),
      repoUrl: project.repoUrl ?? '',
      demoUrl: project.demoUrl ?? '',
      featured: project.featured,
      published: project.published,
    });
  }

  private body(): ProjectUpdate {
    const v = this.form.getRawValue();
    const optional = (value: string) => value.trim() || null;
    return {
      title: { es: v.titleEs.trim(), en: v.titleEn.trim() },
      summary: { es: v.summaryEs.trim(), en: v.summaryEn.trim() },
      description: { es: v.descriptionEs.trim(), en: v.descriptionEn.trim() },
      technologies: parseTechnologies(v.technologies),
      repoUrl: optional(v.repoUrl),
      demoUrl: optional(v.demoUrl),
      imageUrl: this.imageUrl,
      featured: v.featured,
      published: v.published,
    };
  }
}
