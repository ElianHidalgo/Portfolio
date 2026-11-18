import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { formatAdminDate } from '../api/admin-dates';
import { AdminApi } from '../api/admin-api';
import { AdminMessage } from '../api/admin-models';

/** The contact inbox: newest first, mark read or unread, reply by email, delete. */
@Component({
  selector: 'app-messages-page',
  templateUrl: './messages-page.html',
  styleUrl: './messages-page.scss',
})
export class MessagesPage {
  private readonly api = inject(AdminApi);
  private readonly window = inject(DOCUMENT).defaultView;

  protected readonly items = signal<AdminMessage[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly hasMore = computed(() => this.items().length < this.total());
  protected readonly formatDate = formatAdminDate;
  private nextPage = 0;

  constructor() {
    this.loadMore();
  }

  protected loadMore(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.messages(this.nextPage).subscribe({
      next: (page) => {
        this.items.update((items) => [...items, ...page.items]);
        this.total.set(page.total);
        this.nextPage++;
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  protected toggleRead(message: AdminMessage): void {
    const read = !message.read;
    this.api.setRead(message.id, read).subscribe(() =>
      this.items.update((items) => items.map((item) => (item.id === message.id ? { ...item, read } : item))),
    );
  }

  protected remove(message: AdminMessage): void {
    if (!this.window?.confirm('¿Eliminar este mensaje? No se puede deshacer.')) return;
    this.api.deleteMessage(message.id).subscribe(() => {
      this.items.update((items) => items.filter((item) => item.id !== message.id));
      this.total.update((total) => total - 1);
    });
  }
}
