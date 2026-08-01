import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { OrderService } from './order.service';
import { Connection, Order, emptyConnection } from '../../core/graphql/graphql.models';

describe('OrderService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: OrderService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery', 'mutate']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(OrderService);
  });

  it('listMyOrders maps me.orders', (done) => {
    const connection: Connection<Order> = { ...emptyConnection<Order>(), totalCount: 2 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { me: { orders: connection } } }) } as never);

    service.listMyOrders(20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('listAllOrders maps the orders connection', (done) => {
    const connection: Connection<Order> = { ...emptyConnection<Order>(), totalCount: 4 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { orders: connection } }) } as never);

    service.listAllOrders('PAID', 20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('getOrder maps a single order', (done) => {
    const order = { id: '1' } as Order;
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { order } }) } as never);

    service.getOrder('1').subscribe((result) => {
      expect(result).toEqual(order);
      done();
    });
  });

  it('placeOrder maps the created order', (done) => {
    const order = { id: '2' } as Order;
    apollo.mutate.and.returnValue(of({ data: { placeOrder: order } }) as never);

    service.placeOrder({ items: [{ productId: '1', quantity: 1 }] }).subscribe((result) => {
      expect(result).toEqual(order);
      done();
    });
  });

  it('updateOrderStatus maps the updated order', (done) => {
    const order = { id: '2', status: 'SHIPPED' } as Order;
    apollo.mutate.and.returnValue(of({ data: { updateOrderStatus: order } }) as never);

    service.updateOrderStatus('2', 'SHIPPED').subscribe((result) => {
      expect(result).toEqual(order);
      done();
    });
  });
});
