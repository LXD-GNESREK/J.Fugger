import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

// Wird angezeigt, wenn die Rolle für eine Seite nicht reicht (Route Guard).
@Component({
  selector: 'app-forbidden',
  imports: [MatCardModule, MatButtonModule, RouterLink],
  template: `
    <main class="page">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Keine Berechtigung</mat-card-title>
          <mat-card-subtitle>Für diese Seite fehlen dir die nötigen Rechte.</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-stroked-button routerLink="/">Zur Startseite</a>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: `
    .page { padding: 24px; max-width: 600px; margin: 0 auto; }
  `
})
export class Forbidden {}
