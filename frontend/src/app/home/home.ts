import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Roles } from '../auth/roles';

const ROLE_LABELS: Record<string, string> = {
  [Roles.EMPLOYEE]: 'Employee',
  [Roles.MANAGER]: 'Manager',
  [Roles.FINANCE_ADMIN]: 'Finance Admin'
};

// Startseite nach dem Login. Wird in Epic 4 zum Dashboard.
@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatButtonModule],
  template: `
    <main class="page">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Willkommen bei Ledgerly</mat-card-title>
          <mat-card-subtitle>Angemeldet als {{ user()?.username }} ({{ roleLabel() }})</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <button mat-stroked-button (click)="logout()">Abmelden</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: `
    .page { padding: 24px; max-width: 600px; margin: 0 auto; }
  `
})
export class Home {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly roleLabel = computed(() => {
    const role = this.user()?.role ?? '';
    return ROLE_LABELS[role] ?? role;
  });

  protected logout(): void {
    // Auch wenn der Request fehlschlägt: lokal ist der User schon abgemeldet
    const toLogin = () => this.router.navigateByUrl('/login');
    this.auth.logout().subscribe({ complete: toLogin, error: toLogin });
  }
}
