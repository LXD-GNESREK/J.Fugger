import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, Observable, of, shareReplay, tap } from 'rxjs';

export interface CurrentUser {
  username: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  // "Gedächtnis": wer ist eingeloggt? (null = niemand)
  private readonly currentUser = signal<CurrentUser | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);

  // Nur EIN Refresh gleichzeitig: Zwei Refreshes mit demselben Token hält das
  // Backend für Token-Diebstahl und beendet die Sitzung.
  private refreshInFlight: Observable<CurrentUser> | null = null;

  login(username: string, password: string): Observable<CurrentUser> {
    return this.http
      .post<CurrentUser>('/api/auth/login', { username, password })
      .pipe(tap(user => this.currentUser.set(user)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', null)
      .pipe(finalize(() => this.currentUser.set(null)));
  }

  refresh(): Observable<CurrentUser> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<CurrentUser>('/api/auth/refresh', null).pipe(
        tap(user => this.currentUser.set(user)),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1)
      );
    }
    return this.refreshInFlight;
  }

  // Neuladen: "Bin ich noch eingeloggt?" – sonst einmal Refresh versuchen
  restoreSession(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>('/api/auth/me').pipe(
      tap(user => this.currentUser.set(user)),
      catchError(() => this.refresh()),
      catchError(() => {
        this.clearUser();
        return of(null);
      })
    );
  }

  clearUser(): void {
    this.currentUser.set(null);
  }
}
