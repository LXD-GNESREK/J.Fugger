import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { AuthService, CurrentUser } from './auth.service';
import { Roles } from './roles';

// Statt des echten AuthService ein einfacher Ersatz, bei dem wir selbst festlegen,
// wer eingeloggt ist (kein Backend nötig).
function setup(user: CurrentUser | null): void {
  const currentUser = signal(user);
  TestBed.configureTestingModule({
    providers: [
      provideRouter(routes),
      {
        provide: AuthService,
        useValue: {
          user: currentUser.asReadonly(),
          isLoggedIn: computed(() => currentUser() !== null)
        }
      }
    ]
  });
}

// Guard ausführen und Ergebnis als Text: "true" oder die Umleitungs-Adresse
function run(guard: CanActivateFn): string {
  const result = TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
  );
  return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : String(result);
}

const employee: CurrentUser = { username: 'anna', role: Roles.EMPLOYEE };
const manager: CurrentUser = { username: 'max', role: Roles.MANAGER };

describe('authGuard', () => {
  it('schickt nicht eingeloggte User zum Login', () => {
    setup(null);
    expect(run(authGuard)).toBe('/login');
  });

  it('lässt eingeloggte User durch', () => {
    setup(employee);
    expect(run(authGuard)).toBe('true');
  });
});

describe('roleGuard', () => {
  const managerOnly = roleGuard(Roles.MANAGER);

  it('schickt einen Employee auf einer Manager-Seite zu 403', () => {
    setup(employee);
    expect(run(managerOnly)).toBe('/forbidden');
  });

  it('lässt einen Manager auf die Manager-Seite', () => {
    setup(manager);
    expect(run(managerOnly)).toBe('true');
  });

  it('schickt nicht eingeloggte User zum Login (nicht zu 403)', () => {
    setup(null);
    expect(run(managerOnly)).toBe('/login');
  });

  it('erlaubt mehrere Rollen', () => {
    setup(manager);
    expect(run(roleGuard(Roles.MANAGER, Roles.FINANCE_ADMIN))).toBe('true');
  });
});

describe('guestGuard', () => {
  it('zeigt nicht eingeloggten Usern die Login-Seite', () => {
    setup(null);
    expect(run(guestGuard)).toBe('true');
  });

  it('schickt eingeloggte User von der Login-Seite zur Startseite', () => {
    setup(employee);
    expect(run(guestGuard)).toBe('/');
  });
});

// Prüft, dass die Guards in app.routes.ts auch wirklich an den Seiten hängen
describe('Routen', () => {
  it('leitet / ohne Login auf /login um', async () => {
    setup(null);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('leitet unbekannte Adressen ohne Login auf /login um', async () => {
    setup(null);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/gibt-es-nicht');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('leitet /login mit Login auf die Startseite um', async () => {
    setup(employee);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    expect(TestBed.inject(Router).url).toBe('/');
  });
});
