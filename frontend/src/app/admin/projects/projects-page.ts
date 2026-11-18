import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../api/admin-api';
import { AdminProject } from '../api/admin-models';

/** Lists the projects in site order and lets the admin reorder, edit and delete them. */
@Component({
  selector: 'app-projects-page',
  imports: [RouterLink],
  templateUrl: './projects-page.html',
  styleUrl: './projects-page.scss',
})
export class ProjectsPage {
  private readonly api = inject(AdminApi);

  protected readonly projects = signal<AdminProject[]>([]);
  protected readonly loaded = signal(false);
  protected readonly loadFailed = signal(false);
  /** The id of the row whose request is in flight. */
  protected readonly busyId = signal<number | null>(null);
  protected readonly notice = signal('');
  /** The last failure and the row it belongs to. */
  protected readonly failure = signal<{ id: number; text: string } | null>(null);

  constructor() {
    this.load();
  }

  private load(onDone?: () => void): void {
    this.api.projects().subscribe({
      next: (projects) => {
        this.projects.set(projects);
        this.loaded.set(true);
        this.loadFailed.set(false);
        onDone?.();
      },
      error: () => this.loadFailed.set(true),
    });
  }

  /** Swaps a project with its neighbour and sends the full order. */
  protected move(project: AdminProject, step: -1 | 1): void {
    if (this.busyId() !== null) return;
    const all = [...this.projects()];
    const from = all.indexOf(project);
    const to = from + step;
    if (to < 0 || to >= all.length) return;
    [all[from], all[to]] = [all[to], all[from]];
    this.busyId.set(project.id);
    this.clearMessages();
    this.api.reorderProjects(all.map((p) => p.id)).subscribe({
      next: (ordered) => {
        this.projects.set(ordered);
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
          this.fail(project.id, error);
        }
      },
    });
  }

  protected remove(project: AdminProject): void {
    if (this.busyId() !== null) return;
    if (!window.confirm('¿Eliminar este proyecto? No se puede deshacer.')) return;
    this.busyId.set(project.id);
    this.clearMessages();
    this.api.deleteProject(project.id).subscribe({
      next: () => {
        this.projects.update((all) => all.filter((p) => p.id !== project.id));
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.fail(project.id, error);
      },
    });
  }

  protected isFirst(project: AdminProject): boolean {
    return this.projects()[0] === project;
  }

  protected isLast(project: AdminProject): boolean {
    const all = this.projects();
    return all[all.length - 1] === project;
  }

  private clearMessages(): void {
    this.notice.set('');
    this.failure.set(null);
  }

  private fail(id: number, error: unknown): void {
    this.failure.set({
      id,
      text:
        error instanceof HttpErrorResponse && error.status === 409
          ? 'No puedes ocultar ni eliminar el último proyecto publicado.'
          : 'No se pudo guardar. Inténtalo de nuevo.',
    });
  }

  protected failureFor(id: number): string {
    const failure = this.failure();
    return failure && failure.id === id ? failure.text : '';
  }
}
