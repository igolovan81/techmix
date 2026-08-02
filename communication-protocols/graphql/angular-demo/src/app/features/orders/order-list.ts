import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { AuthService } from '../../core/auth/auth.service';
import { Edge, Order, OrderStatus, PageInfo, emptyConnection } from '../../core/graphql/graphql.models';
import { OrderService } from './order.service';
import { PlaceOrder } from './place-order';

const PAGE_SIZE = 20;
const ORDER_STATUSES: OrderStatus[] = ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

@Component({
  selector: 'app-order-list',
  imports: [DecimalPipe, RouterLink, MatTabsModule, MatFormFieldModule, MatSelectModule, ConnectionPaginator, PlaceOrder],
  templateUrl: './order-list.html',
  styleUrl: './order-list.scss',
})
export class OrderList {
  private readonly orderService = inject(OrderService);
  protected readonly authService = inject(AuthService);
  protected readonly statuses = ORDER_STATUSES;

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
