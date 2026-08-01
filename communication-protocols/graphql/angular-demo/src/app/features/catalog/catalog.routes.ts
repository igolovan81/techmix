import { Routes } from '@angular/router';

export const CATALOG_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./product-list').then((m) => m.ProductList) },
  { path: ':id', loadComponent: () => import('./product-detail').then((m) => m.ProductDetail) },
];
