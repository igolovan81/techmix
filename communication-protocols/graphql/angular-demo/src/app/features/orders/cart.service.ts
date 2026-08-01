import { Injectable, computed, signal } from '@angular/core';
import { PlaceOrderInput, Product } from '../../core/graphql/graphql.models';

export interface CartLine {
  product: Product;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly lineMap = signal<Map<string, CartLine>>(new Map());

  readonly lines = computed(() => Array.from(this.lineMap().values()));
  readonly totalCents = computed(() => this.lines().reduce((sum, line) => sum + line.product.priceCents * line.quantity, 0));

  add(product: Product, quantity = 1): void {
    const next = new Map(this.lineMap());
    const existing = next.get(product.id);
    next.set(product.id, { product, quantity: (existing?.quantity ?? 0) + quantity });
    this.lineMap.set(next);
  }

  updateQuantity(productId: string, quantity: number): void {
    const next = new Map(this.lineMap());
    const existing = next.get(productId);
    if (!existing) {
      return;
    }
    if (quantity <= 0) {
      next.delete(productId);
    } else {
      next.set(productId, { ...existing, quantity });
    }
    this.lineMap.set(next);
  }

  remove(productId: string): void {
    const next = new Map(this.lineMap());
    next.delete(productId);
    this.lineMap.set(next);
  }

  clear(): void {
    this.lineMap.set(new Map());
  }

  toPlaceOrderInput(): PlaceOrderInput {
    return { items: this.lines().map((line) => ({ productId: line.product.id, quantity: line.quantity })) };
  }
}
