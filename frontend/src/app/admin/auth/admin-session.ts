import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, timeout } from 'rxjs';
import { API_ORIGIN } from '../../core/http/api-origin';
import { COLD_START_TIMEOUT_MS } from '../../core/http/cold-start';

const STORAGE_KEY = 'portfolio.admin.token';

export type LogoutReason = 'expired';

interface LoginResponse {
  token: string;
  expiresAt: string;
}

/**
 * The admin's sign-in, kept in sessionStorage: it survives reloads but ends with the tab.
 * A cookie is not an option — the API lives on another site, and browsers block those
 * cookies — and the strict CSP is what keeps scripts from reading the token.
 */
@Injectable({ providedIn: 'root' })
export class AdminSession {
  private readonly http = inject(HttpClient);
  private readonly apiOrigin = inject(API_ORIGIN);
  private readonly storage = storageOf(inject(DOCUMENT).defaultView);

  /** Why the last sign-out happened, so the login page can explain it. */
  readonly lastLogoutReason = signal<LogoutReason | null>(null);

  login(username: string, password: string): Observable<void> {
    return this.http.post<LoginResponse>(`${this.apiOrigin}/api/auth/login`, { username, password }).pipe(
      timeout(COLD_START_TIMEOUT_MS),
      map(({ token }) => {
        this.write(token);
        this.lastLogoutReason.set(null);
      }),
    );
  }

  token(): string | null {
    try {
      return this.storage?.getItem(STORAGE_KEY) ?? null;
    } catch {
      return null;
    }
  }

  /** Signed in with a token that has not expired yet (the API checks the signature). */
  isAuthenticated(): boolean {
    const expiresAt = expiryOf(this.token());
    return expiresAt !== null && expiresAt > Date.now();
  }

  logout(reason?: LogoutReason): void {
    try {
      this.storage?.removeItem(STORAGE_KEY);
    } catch {
      // Storage unavailable: nothing was stored either.
    }
    this.lastLogoutReason.set(reason ?? null);
  }

  private write(token: string): void {
    try {
      this.storage?.setItem(STORAGE_KEY, token);
    } catch {
      // Private mode without storage: the session lasts until the page reloads.
    }
  }
}

function storageOf(window: Window | null): Storage | null {
  try {
    return window?.sessionStorage ?? null;
  } catch {
    return null;
  }
}

/** The token's `exp` in milliseconds, or null when the token is missing or malformed. */
function expiryOf(token: string | null): number | null {
  const payload = token?.split('.')[1];
  if (!payload) return null;
  try {
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: unknown }).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}
