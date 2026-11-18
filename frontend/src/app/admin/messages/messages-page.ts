import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
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
  /** Ids with a mark or delete request in flight: their buttons wait. */
  protected readonly busy = signal<ReadonlySet<number>>(new Set());
  protected readonly actionFailed = signal(false);
  protected readonly hasMore = computed(() => this.items().length < this.total());
  protected readonly formatDate = formatAdminDate;

  constructor() {
    this.loadMore();
  }

  protected loadMore(): void {
    this.loading.set(true);
    this.failed.set(false);
    // A cursor (the oldest id shown), not a page number: deletions and new arrivals don't shift it.
    const oldest = this.items().at(-1)?.id;
    this.api.messages(oldest).subscribe({
      next: (page) => {
        this.items.update((items) => {
          const shown = new Set(items.map((item) => item.id));
          return [...items, ...page.items.filter((item) => !shown.has(item.id))];
        });
        this.total.set(page.total);
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
    this.run(message.id, this.api.setRead(message.id, read), () =>
      this.items.update((items) => items.map((item) => (item.id === message.id ? { ...item, read } : item))),
    );
  }

  protected remove(message: AdminMessage): void {
    if (!this.window?.confirm('¿Eliminar este mensaje? No se puede deshacer.')) return;
    this.run(message.id, this.api.deleteMessage(message.id), () => {
      this.items.update((items) => items.filter((item) => item.id !== message.id));
      this.total.update((total) => total - 1);
    });
  }

  /** Runs one action on a message: its buttons wait, and a failure is announced. */
  private run(id: number, action: Observable<void>, onSuccess: () => void): void {
    if (this.busy().has(id)) return;
    this.actionFailed.set(false);
    this.busy.update((ids) => new Set(ids).add(id));
    const done = () =>
      this.busy.update((ids) => {
        const next = new Set(ids);
        next.delete(id);
        return next;
      });
    action.subscribe({
      next: () => {
        done();
        onSuccess();
      },
      error: () => {
        done();
        this.actionFailed.set(true);
      },
    });
  }

  /**
   * A mailto link whose address cannot carry extra headers: the visitor typed the address, and
   * an unencoded "?" or "&" would let them add recipients (bcc) or a body to the reply.
   */
  protected replyHref(email: string): string {
    const at = email.lastIndexOf('@');
    return `mailto:${encodeURIComponent(email.slice(0, at))}@${encodeURIComponent(email.slice(at + 1))}`;
  }
}
