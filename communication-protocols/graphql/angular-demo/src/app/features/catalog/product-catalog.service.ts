import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { AddReviewInput, Connection, Product, ProductFilter, Review, ReviewFilter, emptyConnection } from '../../core/graphql/graphql.models';
import { ADD_REVIEW_MUTATION, DELETE_REVIEW_MUTATION, PRODUCT_QUERY, PRODUCT_REVIEWS_QUERY, PRODUCTS_QUERY } from './catalog.gql';

@Injectable({ providedIn: 'root' })
export class ProductCatalogService {
  private readonly apollo = inject(Apollo);

  listProducts(filter: ProductFilter | null, first: number, after: string | null): Observable<Connection<Product>> {
    return this.apollo
      .watchQuery<{ products: Connection<Product> }>({
        query: PRODUCTS_QUERY,
        variables: { filter, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data!.products as Connection<Product>));
  }

  getProduct(id: string): Observable<Product | null> {
    return this.apollo
      .watchQuery<{ product: Product | null }>({ query: PRODUCT_QUERY, variables: { id }, fetchPolicy: 'network-only' })
      .valueChanges.pipe(map((result) => (result.data!.product ?? null) as Product | null));
  }

  listReviews(
    productId: string,
    filter: ReviewFilter | null,
    first: number,
    after: string | null,
  ): Observable<Connection<Review>> {
    return this.apollo
      .watchQuery<{ product: { reviews: Connection<Review> } | null }>({
        query: PRODUCT_REVIEWS_QUERY,
        variables: { id: productId, filter, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => (result.data!.product?.reviews ?? emptyConnection<Review>()) as Connection<Review>));
  }

  addReview(input: AddReviewInput): Observable<Review> {
    return this.apollo
      .mutate<{ addReview: Review }>({ mutation: ADD_REVIEW_MUTATION, variables: { input } })
      .pipe(map((result) => result.data!.addReview));
  }

  deleteReview(id: string): Observable<boolean> {
    return this.apollo
      .mutate<{ deleteReview: boolean }>({ mutation: DELETE_REVIEW_MUTATION, variables: { id } })
      .pipe(map((result) => result.data!.deleteReview));
  }
}
