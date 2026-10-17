import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth/auth.guard';
import { Login } from './auth/login/login';
import { Forbidden } from './forbidden/forbidden';
import { Home } from './home/home';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'forbidden', component: Forbidden, canActivate: [authGuard] },
  { path: '', component: Home, canActivate: [authGuard] },
  { path: '**', redirectTo: '' } // unbekannte Adresse -> Startseite
];
