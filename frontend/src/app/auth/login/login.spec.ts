import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    // Ersatz für den AuthService: wir bestimmen, was login() zurückgibt
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);

    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const input = (name: string) => page().querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
  const button = () => page().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  const errorText = () => page().querySelector('[role="alert"]')?.textContent?.trim();

  function type(name: string, value: string): void {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    page().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function fillAndSubmit(): void {
    type('username', 'anna');
    type('password', 'geheim');
    submit();
  }

  it('sperrt den Button, solange die Felder leer sind', () => {
    expect(button().disabled).toBeTrue();

    type('username', 'anna');
    expect(button().disabled).toBeTrue();

    type('password', 'geheim');
    expect(button().disabled).toBeFalse();
  });

  it('meldet an und geht zur Startseite', () => {
    auth.login.and.returnValue(of({ username: 'anna', role: 'ROLE_EMPLOYEE' }));

    fillAndSubmit();

    expect(auth.login).toHaveBeenCalledWith('anna', 'geheim');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('zeigt bei falschen Daten (401) die Meldung vom Backend und leert das Passwort', () => {
    const message = 'Benutzername oder Passwort ist falsch.';
    auth.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401, error: { message } })));

    fillAndSubmit();

    expect(errorText()).toBe(message);
    expect(input('password').value).toBe('');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('zeigt bei zu vielen Versuchen (429) die Sperr-Meldung', () => {
    const message = 'Zu viele fehlgeschlagene Anmeldeversuche. Bitte später erneut versuchen.';
    auth.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429, error: { message } })));

    fillAndSubmit();

    expect(errorText()).toBe(message);
  });

  it('zeigt "Server nicht erreichbar", wenn das Backend nicht läuft', () => {
    auth.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    fillAndSubmit();

    expect(errorText()).toBe('Server nicht erreichbar.');
  });
});
