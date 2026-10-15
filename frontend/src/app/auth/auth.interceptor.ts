import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

// Erneuert das abgelaufene Access-Token automatisch.
// Ablauf: 401 -> einmal /api/auth/refresh -> Request wiederholen.
// Schlägt auch der Refresh fehl, ist die Sitzung vorbei -> zur Login-Seite.
// Kein "Authorization"-Header nötig: das JWT liegt im HttpOnly-Cookie, der Browser schickt es selbst mit.

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Auth-Endpunkte nicht abfangen, sonst Endlosschleife
  // (401 beim Login heißt "falsches Passwort", nicht "Token abgelaufen")
  if (req.url.startsWith('/api/auth/')) {
    return next(req);
  }

  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      // Nur bei 401 eingreifen, alle anderen Fehler normal weiterreichen
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }

      // Neues Token holen und den ursprünglichen Request wiederholen
      return auth.refresh().pipe(
        catchError(refreshError => {
          // Refresh hat nicht geklappt -> ausloggen
          auth.clearUser();
          router.navigateByUrl('/login');
          return throwError(() => refreshError);
        }),
        switchMap(() => next(req))
      );
    })
  );
};
