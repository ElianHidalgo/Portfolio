import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, timeout } from 'rxjs';
import { API_ORIGIN } from '../../../core/http/api-origin';
import { COLD_START_TIMEOUT_MS } from '../../../core/http/cold-start';
import { ContactMessage } from '../../../core/models/contact';

@Injectable({ providedIn: 'root' })
export class ContactApi {
  private readonly http = inject(HttpClient);
  private readonly apiOrigin = inject(API_ORIGIN);

  send(message: ContactMessage): Observable<void> {
    return this.http.post(`${this.apiOrigin}/api/contact`, message).pipe(
      timeout(COLD_START_TIMEOUT_MS),
      map(() => undefined),
    );
  }
}
