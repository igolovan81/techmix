import { Component, inject, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { CartService } from './cart.service';
import { OrderService } from './order.service';

@Component({
  selector: 'app-place-order',
  imports: [DecimalPipe, MatButtonModule, MatListModule],
  templateUrl: './place-order.html',
})
export class PlaceOrder {
  readonly cartService = inject(CartService);
  private readonly orderService = inject(OrderService);

  readonly ordered = output<void>();

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
