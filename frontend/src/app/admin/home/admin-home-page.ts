import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatAdminDate } from '../api/admin-dates';
import { AdminApi } from '../api/admin-api';

type PublishState = 'idle' | 'sending' | 'done' | 'limited' | 'unconfigured' | 'error';

/** The panel's home: unread messages, last publication and the "Publicar" button. */
@Component({
  selector: 'app-admin-home-page',
  imports: [RouterLink],
  templateUrl: './admin-home-page.html',
  styleUrl: './admin-home-page.scss',
})
export class AdminHomePage {
  private readonly api = inject(AdminApi);

  protected readonly unread = signal<number | null>(null);
  /** undefined: still loading; null: never published. */
  protected readonly lastPublished = signal<string | null | undefined>(undefined);
  protected readonly publishState = signal<PublishState>('idle');
  protected readonly formatDate = formatAdminDate;

  constructor() {
    this.api.messages(0, 1).subscribe({ next: (page) => this.unread.set(page.unread), error: () => undefined });
    this.api.publishStatus().subscribe({
      next: (status) => this.lastPublished.set(status.lastRequestedAt),
      error: () => this.lastPublished.set(null),
    });
  }

  protected publish(): void {
    if (this.publishState() === 'sending') return;
    this.publishState.set('sending');
    this.api.publish().subscribe({
      next: (status) => {
        this.lastPublished.set(status.lastRequestedAt);
        this.publishState.set('done');
      },
      error: (error: unknown) => {
        const status = error instanceof HttpErrorResponse ? error.status : 0;
        this.publishState.set(status === 429 ? 'limited' : status === 503 ? 'unconfigured' : 'error');
      },
    });
  }
}
