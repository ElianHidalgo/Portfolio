import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, timeout } from 'rxjs';
import { API_ORIGIN } from '../../core/http/api-origin';
import { COLD_START_TIMEOUT_MS } from '../../core/http/cold-start';
import { Profile, SkillCategory } from '../../core/models/profile';
import { AdminSkill, MessagePage, ProfileUpdate, PublishStatus } from './admin-models';

/** The admin API. The interceptor adds the admin token to every call made here. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_ORIGIN)}/api/admin`;

  /** Newest first; `before` is the id of the oldest message already shown (none: first page). */
  messages(before?: number, size = 20): Observable<MessagePage> {
    let params = new HttpParams();
    if (before !== undefined) params = params.set('before', before);
    params = params.set('size', size);
    return this.http.get<MessagePage>(`${this.base}/messages`, { params });
  }

  setRead(id: number, read: boolean): Observable<void> {
    return this.http.patch(`${this.base}/messages/${id}/read`, { read }).pipe(map(() => undefined));
  }

  deleteMessage(id: number): Observable<void> {
    return this.http.delete(`${this.base}/messages/${id}`).pipe(map(() => undefined));
  }

  publishStatus(): Observable<PublishStatus> {
    return this.http.get<PublishStatus>(`${this.base}/publish/status`);
  }

  /** The backend calls Cloudflare synchronously and may be waking up: allow a cold start. */
  publish(): Observable<PublishStatus> {
    return this.http.post<PublishStatus>(`${this.base}/publish`, null).pipe(timeout(COLD_START_TIMEOUT_MS));
  }

  profile(): Observable<Profile> {
    return this.http.get<Profile>(`${this.base}/profile`);
  }

  saveProfile(body: ProfileUpdate): Observable<Profile> {
    return this.http.put<Profile>(`${this.base}/profile`, body);
  }

  createSkill(name: string, category: SkillCategory): Observable<AdminSkill> {
    return this.http.post<AdminSkill>(`${this.base}/skills`, { name, category });
  }

  updateSkill(id: number, name: string, category: SkillCategory): Observable<AdminSkill> {
    return this.http.put<AdminSkill>(`${this.base}/skills/${id}`, { name, category });
  }

  deleteSkill(id: number): Observable<void> {
    return this.http.delete(`${this.base}/skills/${id}`).pipe(map(() => undefined));
  }

  reorderSkills(ids: number[]): Observable<AdminSkill[]> {
    return this.http.put<AdminSkill[]>(`${this.base}/skills/order`, { ids });
  }
}
