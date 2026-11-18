import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Skill, SkillCategory } from '../../core/models/profile';
import { AdminApi } from '../api/admin-api';

const CATEGORIES: ReadonlyArray<{ value: SkillCategory; label: string }> = [
  { value: 'BACKEND', label: 'Backend' },
  { value: 'FRONTEND', label: 'Frontend' },
  { value: 'DATABASE', label: 'Base de datos' },
  { value: 'DEVOPS', label: 'DevOps' },
  { value: 'TOOLS', label: 'Herramientas' },
];

interface Draft {
  name: string;
  category: SkillCategory;
}

/** Lists, adds, edits, reorders and removes the skills shown on the site. */
@Component({
  selector: 'app-skills-page',
  templateUrl: './skills-page.html',
  styleUrl: './skills-page.scss',
})
export class SkillsPage {
  private readonly api = inject(AdminApi);

  protected readonly categories = CATEGORIES;
  /** Every skill in site order (grouping by category keeps that order inside each group). */
  protected readonly skills = signal<Skill[]>([]);
  protected readonly drafts = signal<Record<number, Draft>>({});
  protected readonly loaded = signal(false);
  protected readonly loadFailed = signal(false);
  /** The id of the row whose request is in flight. */
  protected readonly busyId = signal<number | null>(null);
  protected readonly adding = signal(false);
  protected readonly newName = signal('');
  protected readonly newCategory = signal<SkillCategory>('BACKEND');
  protected readonly notice = signal('');
  /** The last failure and where it belongs: the add form or one row. */
  protected readonly failure = signal<{ scope: 'add' | number; text: string } | null>(null);

  protected readonly groups = computed(() =>
    CATEGORIES.map(({ value, label }) => ({
      category: value,
      label,
      skills: this.skills().filter((skill) => skill.category === value),
    })),
  );

  constructor() {
    this.load();
  }

  private load(onDone?: () => void): void {
    this.api.profile().subscribe({
      next: (profile) => {
        this.skills.set(profile.skills);
        this.drafts.set({});
        this.loaded.set(true);
        this.loadFailed.set(false);
        onDone?.();
      },
      error: () => this.loadFailed.set(true),
    });
  }

  protected draft(skill: Skill): Draft {
    return this.drafts()[skill.id] ?? { name: skill.name, category: skill.category };
  }

  protected changed(skill: Skill): boolean {
    const draft = this.draft(skill);
    return draft.name.trim() !== '' && (draft.name.trim() !== skill.name || draft.category !== skill.category);
  }

  protected edit(skill: Skill, change: Partial<Draft>): void {
    this.drafts.update((all) => ({ ...all, [skill.id]: { ...this.draft(skill), ...change } }));
  }

  protected add(): void {
    const name = this.newName().trim();
    if (this.adding() || name === '') return;
    this.adding.set(true);
    this.clearMessages();
    this.api.createSkill(name, this.newCategory()).subscribe({
      next: (created) => {
        this.skills.update((all) => [...all, { id: created.id, name: created.name, category: created.category }]);
        this.newName.set('');
        this.adding.set(false);
      },
      error: (error: unknown) => {
        this.adding.set(false);
        this.fail('add', error);
      },
    });
  }

  protected save(skill: Skill): void {
    if (this.busyId() !== null || !this.changed(skill)) return;
    const draft = this.draft(skill);
    this.busyId.set(skill.id);
    this.clearMessages();
    this.api.updateSkill(skill.id, draft.name.trim(), draft.category).subscribe({
      next: (saved) => {
        const updated: Skill = { id: saved.id, name: saved.name, category: saved.category };
        this.skills.update((all) =>
          saved.category === skill.category
            ? all.map((s) => (s.id === saved.id ? updated : s))
            : [...all.filter((s) => s.id !== saved.id), updated],
        );
        this.dropDraft(skill.id);
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.fail(skill.id, error);
      },
    });
  }

  protected remove(skill: Skill): void {
    if (this.busyId() !== null) return;
    if (!window.confirm('¿Eliminar esta skill?')) return;
    this.busyId.set(skill.id);
    this.clearMessages();
    this.api.deleteSkill(skill.id).subscribe({
      next: () => {
        this.skills.update((all) => all.filter((s) => s.id !== skill.id));
        this.dropDraft(skill.id);
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.fail(skill.id, error);
      },
    });
  }

  /** Swaps a skill with its neighbour in the same category and sends the full order. */
  protected move(skill: Skill, step: -1 | 1): void {
    if (this.busyId() !== null) return;
    const all = [...this.skills()];
    const group = all.filter((s) => s.category === skill.category);
    const neighbour = group[group.indexOf(skill) + step];
    if (!neighbour) return;
    const from = all.indexOf(skill);
    const to = all.indexOf(neighbour);
    [all[from], all[to]] = [all[to], all[from]];
    this.busyId.set(skill.id);
    this.clearMessages();
    this.api.reorderSkills(all.map((s) => s.id)).subscribe({
      next: (ordered) => {
        const known = new Map(all.map((s) => [s.id, s]));
        this.skills.set(ordered.map((s) => known.get(s.id) ?? { id: s.id, name: s.name, category: s.category }));
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 400) {
          this.load(() => {
            this.busyId.set(null);
            this.notice.set('La lista cambió en otra pestaña. Se recargó.');
          });
        } else {
          this.busyId.set(null);
          this.fail(skill.id, error);
        }
      },
    });
  }

  protected isFirst(skill: Skill): boolean {
    return this.skills().find((s) => s.category === skill.category) === skill;
  }

  protected isLast(skill: Skill): boolean {
    const group = this.skills().filter((s) => s.category === skill.category);
    return group[group.length - 1] === skill;
  }

  private dropDraft(id: number): void {
    this.drafts.update(({ [id]: _removed, ...rest }) => rest);
  }

  private clearMessages(): void {
    this.notice.set('');
    this.failure.set(null);
  }

  private fail(scope: 'add' | number, error: unknown): void {
    this.failure.set({
      scope,
      text:
        error instanceof HttpErrorResponse && error.status === 409
          ? 'Ya existe una skill con ese nombre.'
          : 'No se pudo guardar. Inténtalo de nuevo.',
    });
  }

  protected failureFor(scope: 'add' | number): string {
    const failure = this.failure();
    return failure && failure.scope === scope ? failure.text : '';
  }
}
