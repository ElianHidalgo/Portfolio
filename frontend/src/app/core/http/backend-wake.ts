import { Injectable, inject } from '@angular/core';
import { API_ORIGIN } from './api-origin';

/**
 * Wakes the free backend (it sleeps after 15 minutes idle and takes 30–120 s to start), so it
 * is ready by the time a visitor finishes writing. The liveness check never touches the
 * database, and `no-cors` needs no CORS answer: the response is never read.
 */
@Injectable({ providedIn: 'root' })
export class BackendWake {
  private readonly apiOrigin = inject(API_ORIGIN);
  private woken = false;

  wake(): void {
    if (this.woken) return;
    this.woken = true;
    fetch(`${this.apiOrigin}/api/health/liveness`, { mode: 'no-cors', cache: 'no-store' }).catch(() => undefined);
  }
}
