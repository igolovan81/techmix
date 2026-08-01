export type ID = string;

export interface PageInfo {
  hasNextPage: boolean;
  endCursor: string | null;
}

export interface Edge<T> {
  node: T;
  cursor: string;
}

export interface Connection<T> {
  edges: Edge<T>[];
  pageInfo: PageInfo;
  totalCount: number;
}

export function emptyConnection<T>(): Connection<T> {
  return { edges: [], pageInfo: { hasNextPage: false, endCursor: null }, totalCount: 0 };
}

export type Role = 'CUSTOMER' | 'ADMIN';
export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface Category {
  id: ID;
  name: string;
  parent?: Category | null;
}

export interface Product {
  id: ID;
  name: string;
  priceCents: number;
  stockQty: number;
  categories?: Category[];
}

export interface User {
  id: ID;
  username: string;
  displayName: string;
  role: Role;
}

export interface Review {
  id: ID;
  productId: ID;
  author: User;
  rating: number;
  comment: string | null;
}

export interface OrderItem {
  id: ID;
  product: Product;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

export interface Order {
  id: ID;
  user: User;
  status: OrderStatus;
  placedAt: string;
  items: OrderItem[];
  totalCents: number;
}

export interface ProductFilter {
  nameContains?: string;
  minPriceCents?: number;
  maxPriceCents?: number;
}

export interface ReviewFilter {
  minRating?: number;
}

export interface AddReviewInput {
  productId: ID;
  rating: number;
  comment?: string;
}

export interface OrderItemInput {
  productId: ID;
  quantity: number;
}

export interface PlaceOrderInput {
  items: OrderItemInput[];
}
