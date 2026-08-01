import { Routes } from '@angular/router';

export const ORDERS_ROUTES: Routes = [{ path: '', loadComponent: () => import('./order-list').then((m) => m.OrderList) }];
