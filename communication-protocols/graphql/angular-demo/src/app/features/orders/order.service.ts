import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { Connection, Order, OrderStatus, PlaceOrderInput } from '../../core/graphql/graphql.models';
import { ALL_ORDERS_QUERY, MY_ORDERS_QUERY, ORDER_QUERY, PLACE_ORDER_MUTATION, UPDATE_ORDER_STATUS_MUTATION } from './orders.gql';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly apollo = inject(Apollo);

  listMyOrders(first: number, after: string | null): Observable<Connection<Order>> {
    return this.apollo
      .watchQuery<{ me: { orders: Connection<Order> } }>({
        query: MY_ORDERS_QUERY,
        variables: { first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data!.me!.orders as Connection<Order>));
  }

  listAllOrders(status: OrderStatus | null, first: number, after: string | null): Observable<Connection<Order>> {
    return this.apollo
      .watchQuery<{ orders: Connection<Order> }>({
        query: ALL_ORDERS_QUERY,
        variables: { status, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data!.orders as Connection<Order>));
  }

  getOrder(id: string): Observable<Order | null> {
    return this.apollo
      .watchQuery<{ order: Order | null }>({ query: ORDER_QUERY, variables: { id }, fetchPolicy: 'network-only' })
      .valueChanges.pipe(map((result) => (result.data!.order ?? null) as Order | null));
  }

  placeOrder(input: PlaceOrderInput): Observable<Order> {
    return this.apollo
      .mutate<{ placeOrder: Order }>({ mutation: PLACE_ORDER_MUTATION, variables: { input } })
      .pipe(map((result) => result.data!.placeOrder as Order));
  }

  updateOrderStatus(id: string, status: OrderStatus): Observable<Order> {
    return this.apollo
      .mutate<{ updateOrderStatus: Order }>({ mutation: UPDATE_ORDER_STATUS_MUTATION, variables: { id, status } })
      .pipe(map((result) => result.data!.updateOrderStatus as Order));
  }
}
