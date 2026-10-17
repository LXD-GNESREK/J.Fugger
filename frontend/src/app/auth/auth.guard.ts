import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// Nur Komfort für die UI: die echte Prüfung macht immer das Backend.

/** Nur für eingeloggte User, sonst -> Login */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
};

/** Nur für bestimmte Rollen. Nicht eingeloggt -> Login, falsche Rolle -> 403 */
export function roleGuard(...allowedRoles: string[]): CanActivateFn {
  return () => {
    const user = inject(AuthService).user();
    const router = inject(Router);

    if (!user) {
      return router.createUrlTree(['/login']);
    }
    return allowedRoles.includes(user.role) ? true : router.createUrlTree(['/forbidden']);
  };
}

/** Login-Seite: wer schon eingeloggt ist, kommt direkt zur Startseite */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).createUrlTree(['/']) : true;
};
