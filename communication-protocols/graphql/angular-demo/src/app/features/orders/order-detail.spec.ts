import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OrderDetail } from './order-detail';
import { OrderService } from './order.service';
import { AuthService } from '../../core/auth/auth.service';
import { Order } from '../../core/graphql/graphql.models';

describe('OrderDetail', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let authService: AuthService;

  const order: Order = {
    id: '1',
    user: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    status: 'PENDING',
    placedAt: '2026-08-01T00:00:00Z',
    items: [{ id: '1', product: { id: '9', name: 'Widget', priceCents: 500, stockQty: 1 }, quantity: 2, unitPriceCents: 500, lineTotalCents: 1000 }],
    totalCents: 1000,
  };

  beforeEach(() => {
    sessionStorage.clear();
    orderService = jasmine.createSpyObj<OrderService>(['getOrder', 'updateOrderStatus']);
    orderService.getOrder.and.returnValue(of(order));
    TestBed.configureTestingModule({
      imports: [OrderDetail],
      providers: [
        provideNoopAnimations(),
        { provide: OrderService, useValue: orderService },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
      ],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads the order for the route id', () => {
    const fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();

    expect(orderService.getOrder).toHaveBeenCalledWith('1');
    expect(fixture.componentInstance.order()).toEqual(order);
  });

  it('hides the status-update control for a non-admin user', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="status-select"]')).toBeNull();
  });

  it('shows and wires the status-update control for an admin user', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });
    orderService.updateOrderStatus.and.returnValue(of({ ...order, status: 'SHIPPED' }));

    const fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();
    fixture.componentInstance.updateStatus('SHIPPED');

    expect(orderService.updateOrderStatus).toHaveBeenCalledWith('1', 'SHIPPED');
    expect(fixture.componentInstance.order()?.status).toBe('SHIPPED');
  });
});
