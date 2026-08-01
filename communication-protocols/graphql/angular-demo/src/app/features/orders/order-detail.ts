import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { AuthService } from '../../core/auth/auth.service';
import { Order, OrderStatus } from '../../core/graphql/graphql.models';
import { OrderService } from './order.service';

const ORDER_STATUSES: OrderStatus[] = ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

@Component({
  selector: 'app-order-detail',
  imports: [DecimalPipe, MatSelectModule, MatListModule],
  templateUrl: './order-detail.html',
})
export class OrderDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);
  protected readonly authService = inject(AuthService);
  protected readonly statuses = ORDER_STATUSES;

  private readonly orderId = this.route.snapshot.paramMap.get('id')!;

  readonly order = signal<Order | null>(null);

  constructor() {
    this.orderService.getOrder(this.orderId).subscribe((order) => this.order.set(order));
  }

  updateStatus(status: OrderStatus): void {
    this.orderService.updateOrderStatus(this.orderId, status).subscribe((order) => this.order.set(order));
  }
}
