import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, timeout } from 'rxjs';
import { API_ORIGIN } from '../../../core/http/api-origin';
import { ContactMessage } from '../../../core/models/contact';

/**
 * Longer than the backend's worst cold start (~2 minutes on Render's free plan): giving up
 * earlier could make the visitor resend a message the waking server still stores.
 */
export const SEND_TIMEOUT_MS = 130_000;

@Injectable({ providedIn: 'root' })
export class ContactApi {
  private readonly http = inject(HttpClient);
  private readonly apiOrigin = inject(API_ORIGIN);

  send(message: ContactMessage): Observable<void> {
    return this.http.post(`${this.apiOrigin}/api/contact`, message).pipe(
      timeout(SEND_TIMEOUT_MS),
      map(() => undefined),
    );
  }
}
