import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { LiveReviews } from './live-reviews';
import { LiveReviewsService } from './live-reviews.service';
import { ProductCatalogService } from '../catalog/product-catalog.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('LiveReviews', () => {
  let liveReviewsService: jasmine.SpyObj<LiveReviewsService>;
  let catalog: jasmine.SpyObj<ProductCatalogService>;

  const review: Review = { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } };
  const products: Connection<Product> = { ...emptyConnection<Product>(), edges: [{ cursor: 'p', node: { id: '1', name: 'Widget', priceCents: 500, stockQty: 1 } }] };

  beforeEach(() => {
    liveReviewsService = jasmine.createSpyObj<LiveReviewsService>(['subscribeToReviewAdded']);
    liveReviewsService.subscribeToReviewAdded.and.returnValue(of(review));
    catalog = jasmine.createSpyObj<ProductCatalogService>(['listProducts']);
    catalog.listProducts.and.returnValue(of(products));

    TestBed.configureTestingModule({
      imports: [LiveReviews],
      providers: [
        provideNoopAnimations(),
        { provide: LiveReviewsService, useValue: liveReviewsService },
        { provide: ProductCatalogService, useValue: catalog },
      ],
    });
  });

  it('subscribes to every product on init and appends incoming reviews to the feed', () => {
    const fixture = TestBed.createComponent(LiveReviews);
    fixture.detectChanges();

    expect(liveReviewsService.subscribeToReviewAdded).toHaveBeenCalledWith(null);
    expect(fixture.componentInstance.feed()).toEqual([review]);
  });

  it('resubscribes with the selected productId when the filter changes', () => {
    const fixture = TestBed.createComponent(LiveReviews);
    fixture.detectChanges();

    fixture.componentInstance.selectProduct('1');

    expect(liveReviewsService.subscribeToReviewAdded).toHaveBeenCalledWith('1');
  });
});
