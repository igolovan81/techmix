import { Component, inject, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CartService } from './cart.service';
import { OrderService } from './order.service';

@Component({
  selector: 'app-place-order',
  imports: [DecimalPipe, MatButtonModule, MatIconModule],
  templateUrl: './place-order.html',
  styleUrl: './place-order.scss',
})
export class PlaceOrder {
  readonly cartService = inject(CartService);
  private readonly orderService = inject(OrderService);

  readonly ordered = output<void>();

  removeLine(productId: string): void {
    this.cartService.remove(productId);
  }

  submit(): void {
    if (this.cartService.lines().length === 0) {
      return;
    }
    this.orderService.placeOrder(this.cartService.toPlaceOrderInput()).subscribe(() => {
      this.cartService.clear();
      this.ordered.emit();
    });
  }
}
