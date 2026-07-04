import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'signals' },
  {
    path: 'signals',
    loadComponent: () => import('./features/signals/signals-demo').then((m) => m.SignalsDemo),
  },
  {
    path: 'component-communication',
    loadComponent: () =>
      import('./features/component-communication/component-communication-demo').then(
        (m) => m.ComponentCommunicationDemo,
      ),
  },
  {
    path: 'forms',
    loadComponent: () => import('./features/forms/forms-demo').then((m) => m.FormsDemo),
  },
];
