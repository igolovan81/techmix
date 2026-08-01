import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { Product } from '../../core/graphql/graphql.models';

describe('CartService', () => {
  let service: CartService;
  const widget: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };
  const gadget: Product = { id: '2', name: 'Gadget', priceCents: 900, stockQty: 5 };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CartService);
  });

  it('starts empty', () => {
    expect(service.lines()).toEqual([]);
    expect(service.totalCents()).toBe(0);
  });

  it('add() adds a new line, and adding the same product again increases its quantity', () => {
    service.add(widget);
    service.add(widget, 2);

    expect(service.lines()).toEqual([{ product: widget, quantity: 3 }]);
    expect(service.totalCents()).toBe(1500);
  });

  it('updateQuantity() changes a line quantity, and removes it when set to 0', () => {
    service.add(widget);
    service.add(gadget);

    service.updateQuantity('1', 5);
    expect(service.lines()).toContain({ product: widget, quantity: 5 });

    service.updateQuantity('1', 0);
    expect(service.lines()).toEqual([{ product: gadget, quantity: 1 }]);
  });

  it('remove() removes a line', () => {
    service.add(widget);
    service.add(gadget);

    service.remove('1');

    expect(service.lines()).toEqual([{ product: gadget, quantity: 1 }]);
  });

  it('clear() empties the cart', () => {
    service.add(widget);

    service.clear();

    expect(service.lines()).toEqual([]);
  });

  it('toPlaceOrderInput() maps lines to a PlaceOrderInput', () => {
    service.add(widget, 2);
    service.add(gadget, 1);

    expect(service.toPlaceOrderInput()).toEqual({
      items: [
        { productId: '1', quantity: 2 },
        { productId: '2', quantity: 1 },
      ],
    });
  });
});
