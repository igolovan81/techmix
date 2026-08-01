import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'catalog' },
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: 'catalog',
    canActivate: [authGuard],
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.CATALOG_ROUTES),
  },
  {
    path: 'categories',
    canActivate: [authGuard],
    loadComponent: () => import('./features/categories/category-tree').then((m) => m.CategoryTree),
  },
  {
    path: 'live',
    canActivate: [authGuard],
    loadComponent: () => import('./features/live-reviews/live-reviews').then((m) => m.LiveReviews),
  },
];
