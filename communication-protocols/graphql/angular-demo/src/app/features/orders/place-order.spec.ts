import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PlaceOrder } from './place-order';
import { OrderService } from './order.service';
import { CartService } from './cart.service';
import { Order, Product } from '../../core/graphql/graphql.models';

describe('PlaceOrder', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let cartService: CartService;
  const widget: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };

  beforeEach(() => {
    orderService = jasmine.createSpyObj<OrderService>(['placeOrder']);
    TestBed.configureTestingModule({
      imports: [PlaceOrder],
      providers: [{ provide: OrderService, useValue: orderService }],
    });
    cartService = TestBed.inject(CartService);
  });

  it('shows the current cart lines and total', () => {
    cartService.add(widget, 2);

    const fixture = TestBed.createComponent(PlaceOrder);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Widget');
    expect(fixture.componentInstance.cartService.totalCents()).toBe(1000);
  });

  it('placing the order submits the cart and clears it on success', () => {
    cartService.add(widget, 2);
    const placedOrder = { id: '5' } as Order;
    orderService.placeOrder.and.returnValue(of(placedOrder));

    const fixture = TestBed.createComponent(PlaceOrder);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    expect(orderService.placeOrder).toHaveBeenCalledWith({ items: [{ productId: '1', quantity: 2 }] });
    expect(cartService.lines()).toEqual([]);
  });

  it('clicking the remove button on a line removes it from the cart', () => {
    cartService.add(widget, 2);

    const fixture = TestBed.createComponent(PlaceOrder);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="remove-line-1"]');
    button.click();

    expect(cartService.lines()).toEqual([]);
  });
});
