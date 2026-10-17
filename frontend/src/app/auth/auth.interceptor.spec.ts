import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService, CurrentUser } from './auth.service';

// Statt eines echten Backends spielt HttpTestingController das Backend:
// wir sagen ihm, was es auf welchen Request antworten soll.
describe('authInterceptor', () => {
  const anna: CurrentUser = { username: 'anna', role: 'ROLE_EMPLOYEE' };
  const unauthorized = { status: 401, statusText: 'Unauthorized' };
  const forbidden = { status: 403, statusText: 'Forbidden' };

  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    // Gleiche Einstellungen wie in app.config.ts
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
          withInterceptors([authInterceptor])
        ),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => {
    backend.verify(); // keine unerwarteten Requests übrig
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  function loginAsAnna(): void {
    auth.login('anna', 'pw').subscribe();
    backend.expectOne('/api/auth/login').flush(anna);
  }

  describe('Token abgelaufen (401)', () => {
    it('holt ein neues Token und wiederholt den Request', () => {
      let result: unknown;
      http.get('/api/expenses').subscribe(r => (result = r));

      backend.expectOne('/api/expenses').flush(null, unauthorized);
      backend.expectOne('/api/auth/refresh').flush(anna);
      backend.expectOne('/api/expenses').flush([{ id: 1 }]);

      expect(result).toEqual([{ id: 1 }]);
      expect(auth.user()).toEqual(anna);
    });

    it('macht bei mehreren gleichzeitigen 401 nur EINEN Refresh (Token-Rotation)', () => {
      let succeeded = 0;
      http.get('/api/a').subscribe(() => succeeded++);
      http.get('/api/b').subscribe(() => succeeded++);
      http.get('/api/c').subscribe(() => succeeded++);

      backend.expectOne('/api/a').flush(null, unauthorized);
      backend.expectOne('/api/b').flush(null, unauthorized);
      backend.expectOne('/api/c').flush(null, unauthorized);

      // Genau EIN Refresh-Request, nicht drei
      const refreshes = backend.match('/api/auth/refresh');
      expect(refreshes.length).toBe(1);
      refreshes[0].flush(anna);

      backend.expectOne('/api/a').flush({});
      backend.expectOne('/api/b').flush({});
      backend.expectOne('/api/c').flush({});
      expect(succeeded).toBe(3);
    });

    it('loggt aus und schickt zum Login, wenn der Refresh fehlschlägt', () => {
      loginAsAnna();
      let error: HttpErrorResponse | undefined;
      http.get('/api/expenses').subscribe({ error: e => (error = e) });

      backend.expectOne('/api/expenses').flush(null, unauthorized);
      backend.expectOne('/api/auth/refresh').flush({ message: 'Sitzung abgelaufen.' }, unauthorized);

      expect(auth.isLoggedIn()).toBeFalse();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
      expect(error?.status).toBe(401);
    });

    it('lässt den User eingeloggt, wenn nach dem Refresh 403 kommt', () => {
      loginAsAnna();
      let error: HttpErrorResponse | undefined;
      http.get('/api/expenses/team').subscribe({ error: e => (error = e) });

      backend.expectOne('/api/expenses/team').flush(null, unauthorized);
      backend.expectOne('/api/auth/refresh').flush(anna);
      backend.expectOne('/api/expenses/team').flush(null, forbidden);

      expect(error?.status).toBe(403);
      expect(auth.isLoggedIn()).toBeTrue();
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('keine Endlosschleife', () => {
    it('macht keinen Refresh, wenn der Login fehlschlägt (falsches Passwort)', () => {
      let error: HttpErrorResponse | undefined;
      auth.login('anna', 'falsch').subscribe({ error: e => (error = e) });

      backend.expectOne('/api/auth/login').flush({ message: 'Benutzername oder Passwort ist falsch.' }, unauthorized);

      backend.expectNone('/api/auth/refresh');
      expect(error?.status).toBe(401);
      expect(auth.isLoggedIn()).toBeFalse();
    });

    it('macht keinen Refresh bei anderen Fehlern (z. B. 500)', () => {
      let error: HttpErrorResponse | undefined;
      http.get('/api/expenses').subscribe({ error: e => (error = e) });

      backend.expectOne('/api/expenses').flush(null, { status: 500, statusText: 'Server Error' });

      backend.expectNone('/api/auth/refresh');
      expect(error?.status).toBe(500);
    });
  });

  describe('CSRF-Schutz', () => {
    beforeEach(() => {
      // So wie das Backend das Cookie setzen würde
      document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/';
    });

    it('schickt den X-XSRF-TOKEN-Header bei POST, PUT und DELETE mit', () => {
      http.post('/api/expenses', {}).subscribe();
      http.put('/api/expenses/1', {}).subscribe();
      http.delete('/api/expenses/1').subscribe();

      for (const method of ['POST', 'PUT', 'DELETE']) {
        const req = backend.expectOne(r => r.method === method);
        expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token');
        req.flush({});
      }
    });

    it('schickt den X-XSRF-TOKEN-Header auch beim Logout mit', () => {
      auth.logout().subscribe();

      const req = backend.expectOne('/api/auth/logout');
      expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token');
      req.flush(null);
    });

    it('schickt KEINEN CSRF-Header bei GET (liest nur, ändert nichts)', () => {
      http.get('/api/expenses').subscribe();

      const req = backend.expectOne('/api/expenses');
      expect(req.request.headers.has('X-XSRF-TOKEN')).toBeFalse();
      req.flush([]);
    });
  });
});
