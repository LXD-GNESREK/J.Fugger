import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Gleiche Grenzen wie im Backend (LoginRequest)
  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(72)]]
  });

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err: HttpErrorResponse) => {
        this.error.set(errorMessage(err));
        this.form.controls.password.reset(); // Passwort nicht stehen lassen
        this.loading.set(false);
      }
    });
  }
}

function errorMessage(err: HttpErrorResponse): string {
  // Bei 401 (falsche Daten) und 429 (gesperrt) schickt das Backend die Meldung mit
  if (err.status === 401 || err.status === 429) {
    return err.error?.message ?? 'Anmeldung fehlgeschlagen.';
  }
  if (err.status === 0) {
    return 'Server nicht erreichbar.';
  }
  return 'Anmeldung fehlgeschlagen. Bitte später erneut versuchen.';
}
