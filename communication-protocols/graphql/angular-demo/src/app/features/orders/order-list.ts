import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { AuthService } from '../../core/auth/auth.service';
import { Edge, Order, OrderStatus, PageInfo, emptyConnection } from '../../core/graphql/graphql.models';
import { OrderService } from './order.service';
import { PlaceOrder } from './place-order';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-order-list',
  imports: [RouterLink, MatTabsModule, MatSelectModule, MatListModule, ConnectionPaginator, PlaceOrder],
  templateUrl: './order-list.html',
})
export class OrderList {
  private readonly orderService = inject(OrderService);
  protected readonly authService = inject(AuthService);

  readonly myOrderEdges = signal<Edge<Order>[]>([]);
  readonly myOrderPageInfo = signal<PageInfo>(emptyConnection<Order>().pageInfo);
  readonly myOrderTotalCount = signal(0);

  readonly allOrderEdges = signal<Edge<Order>[]>([]);
  readonly allOrderPageInfo = signal<PageInfo>(emptyConnection<Order>().pageInfo);
  readonly allOrderTotalCount = signal(0);
  readonly statusFilter = signal<OrderStatus | null>(null);

  constructor() {
    this.loadMyOrders(null);
    if (this.authService.currentUser()?.role === 'ADMIN') {
      this.loadAllOrders(null);
    }
  }

  loadMoreMyOrders(): void {
    this.loadMyOrders(this.myOrderPageInfo().endCursor);
  }

  loadMoreAllOrders(): void {
    this.loadAllOrders(this.allOrderPageInfo().endCursor);
  }

  refreshMyOrders(): void {
    this.myOrderEdges.set([]);
    this.loadMyOrders(null);
  }

  filterAllOrdersByStatus(status: OrderStatus | null): void {
    this.statusFilter.set(status);
    this.allOrderEdges.set([]);
    this.loadAllOrders(null);
  }

  private loadMyOrders(after: string | null): void {
    this.orderService.listMyOrders(PAGE_SIZE, after).subscribe((connection) => {
      this.myOrderEdges.set([...this.myOrderEdges(), ...connection.edges]);
      this.myOrderPageInfo.set(connection.pageInfo);
      this.myOrderTotalCount.set(connection.totalCount);
    });
  }

  private loadAllOrders(after: string | null): void {
    this.orderService.listAllOrders(this.statusFilter(), PAGE_SIZE, after).subscribe((connection) => {
      this.allOrderEdges.set([...this.allOrderEdges(), ...connection.edges]);
      this.allOrderPageInfo.set(connection.pageInfo);
      this.allOrderTotalCount.set(connection.totalCount);
    });
  }
}
