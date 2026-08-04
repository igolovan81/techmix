import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, filter, map } from 'rxjs';
import { Category, Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';
import { CATEGORIES_QUERY, CATEGORY_CHILDREN_QUERY, CATEGORY_PRODUCTS_QUERY } from './categories.gql';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly apollo = inject(Apollo);

  listCategories(first: number, after: string | null): Observable<Connection<Category>> {
    return this.apollo
      .watchQuery<{ categories: Connection<Category> }>({
        query: CATEGORIES_QUERY,
        variables: { first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(
        filter((result) => result.data !== undefined),
        map((result) => result.data!.categories as Connection<Category>),
      );
  }

  listChildren(categoryId: string, first: number, after: string | null): Observable<Connection<Category>> {
    return this.apollo
      .watchQuery<{ category: { children: Connection<Category> } | null }>({
        query: CATEGORY_CHILDREN_QUERY,
        variables: { id: categoryId, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(
        filter((result) => result.data !== undefined),
        map((result) => (result.data!.category?.children ?? emptyConnection<Category>()) as Connection<Category>),
      );
  }

  listProducts(categoryId: string, first: number, after: string | null): Observable<Connection<Product>> {
    return this.apollo
      .watchQuery<{ category: { products: Connection<Product> } | null }>({
        query: CATEGORY_PRODUCTS_QUERY,
        variables: { id: categoryId, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(
        filter((result) => result.data !== undefined),
        map((result) => (result.data!.category?.products ?? emptyConnection<Product>()) as Connection<Product>),
      );
  }
}
