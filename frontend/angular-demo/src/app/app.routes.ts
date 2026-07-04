import { Routes } from '@angular/router';
import { hasSelectionGuard } from './features/routing/has-selection.guard';
import { itemDetailResolver } from './features/routing/item-detail.resolver';

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
  {
    path: 'data-fetching',
    loadComponent: () =>
      import('./features/data-fetching/data-fetching-demo').then((m) => m.DataFetchingDemo),
  },
  {
    path: 'deferred-loading',
    loadComponent: () =>
      import('./features/deferred-loading/deferred-loading-demo').then((m) => m.DeferredLoadingDemo),
  },
  {
    path: 'routing',
    loadComponent: () => import('./features/routing/routing-demo').then((m) => m.RoutingDemo),
    children: [
      {
        path: 'item/:id',
        canActivate: [hasSelectionGuard],
        resolve: { item: itemDetailResolver },
        loadComponent: () => import('./features/routing/item-detail').then((m) => m.ItemDetail),
      },
    ],
  },
  {
    path: 'pipes',
    loadComponent: () => import('./features/pipes/pipes-demo').then((m) => m.PipesDemo),
  },
  {
    path: 'directives',
    loadComponent: () => import('./features/directives/directives-demo').then((m) => m.DirectivesDemo),
  },
  {
    path: 'animations',
    loadComponent: () => import('./features/animations/animations-demo').then((m) => m.AnimationsDemo),
  },
];
