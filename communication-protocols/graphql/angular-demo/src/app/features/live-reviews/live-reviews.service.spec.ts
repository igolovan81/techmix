import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { LiveReviewsService } from './live-reviews.service';
import { Review } from '../../core/graphql/graphql.models';

describe('LiveReviewsService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: LiveReviewsService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['subscribe']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(LiveReviewsService);
  });

  it('subscribes with the given productId and maps emitted reviews', (done) => {
    const review: Review = { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } };
    apollo.subscribe.and.returnValue(of({ data: { reviewAdded: review } }) as never);

    service.subscribeToReviewAdded('1').subscribe((result) => {
      expect(apollo.subscribe).toHaveBeenCalledWith(
        jasmine.objectContaining({ variables: { productId: '1' } }),
      );
      expect(result).toEqual(review);
      done();
    });
  });

  it('subscribes with a null productId to receive every review', (done) => {
    const review: Review = { id: '10', productId: '2', rating: 3, comment: null, author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } };
    apollo.subscribe.and.returnValue(of({ data: { reviewAdded: review } }) as never);

    service.subscribeToReviewAdded(null).subscribe((result) => {
      expect(apollo.subscribe).toHaveBeenCalledWith(jasmine.objectContaining({ variables: { productId: null } }));
      expect(result).toEqual(review);
      done();
    });
  });
});
