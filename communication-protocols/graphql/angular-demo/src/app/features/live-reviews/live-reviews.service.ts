import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { Review } from '../../core/graphql/graphql.models';
import { REVIEW_ADDED_SUBSCRIPTION } from './live-reviews.gql';

@Injectable({ providedIn: 'root' })
export class LiveReviewsService {
  private readonly apollo = inject(Apollo);

  subscribeToReviewAdded(productId: string | null): Observable<Review> {
    return this.apollo
      .subscribe<{ reviewAdded: Review }>({ query: REVIEW_ADDED_SUBSCRIPTION, variables: { productId } })
      .pipe(
        map((result) => {
          if (!result.data) {
            throw new Error('reviewAdded subscription returned no data');
          }
          return result.data.reviewAdded as Review;
        }),
      );
  }
}
