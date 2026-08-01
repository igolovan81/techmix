import { TestBed } from '@angular/core/testing';
import { RouterModule, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OrderList } from './order-list';
import { OrderService } from './order.service';
import { AuthService } from '../../core/auth/auth.service';
import { Connection, Order, emptyConnection } from '../../core/graphql/graphql.models';

describe('OrderList', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let authService: AuthService;

  const myOrders: Connection<Order> = { ...emptyConnection<Order>(), edges: [{ cursor: 'o1', node: { id: '1' } as Order }], totalCount: 1 };
  const allOrders: Connection<Order> = { ...emptyConnection<Order>(), edges: [{ cursor: 'o2', node: { id: '2' } as Order }], totalCount: 1 };

  beforeEach(() => {
    sessionStorage.clear();
    orderService = jasmine.createSpyObj<OrderService>(['listMyOrders', 'listAllOrders']);
    orderService.listMyOrders.and.returnValue(of(myOrders));
    orderService.listAllOrders.and.returnValue(of(allOrders));
    TestBed.configureTestingModule({
      imports: [OrderList, RouterModule],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: OrderService, useValue: orderService }],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads only "my orders" for a CUSTOMER', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(OrderList);
    fixture.detectChanges();

    expect(orderService.listMyOrders).toHaveBeenCalledWith(20, null);
    expect(orderService.listAllOrders).not.toHaveBeenCalled();
    expect(fixture.componentInstance.myOrderEdges().length).toBe(1);
  });

  it('also loads "all orders" for an ADMIN', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });

    const fixture = TestBed.createComponent(OrderList);
    fixture.detectChanges();

    expect(orderService.listMyOrders).toHaveBeenCalledWith(20, null);
    expect(orderService.listAllOrders).toHaveBeenCalledWith(null, 20, null);
    expect(fixture.componentInstance.allOrderEdges().length).toBe(1);
  });
});
