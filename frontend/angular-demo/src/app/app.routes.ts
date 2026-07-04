import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'signals' },
  {
    path: 'signals',
    loadComponent: () => import('./features/signals/signals-demo').then((m) => m.SignalsDemo),
  },
];
