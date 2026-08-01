import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ProductDetail } from './product-detail';
import { ProductCatalogService } from './product-catalog.service';
import { AuthService } from '../../core/auth/auth.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductDetail', () => {
  let catalog: jasmine.SpyObj<ProductCatalogService>;
  let authService: AuthService;

  const product: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10, categories: [] };
  const reviewsPage: Connection<Review> = {
    edges: [
      {
        cursor: 'r1',
        node: { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } },
      },
    ],
    pageInfo: { hasNextPage: false, endCursor: 'r1' },
    totalCount: 1,
  };

  beforeEach(() => {
    sessionStorage.clear();
    catalog = jasmine.createSpyObj<ProductCatalogService>(['getProduct', 'listReviews', 'deleteReview']);
    catalog.getProduct.and.returnValue(of(product));
    catalog.listReviews.and.returnValue(of(reviewsPage));
    TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductCatalogService, useValue: catalog },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
      ],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads the product and its reviews for the route id', () => {
    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(catalog.getProduct).toHaveBeenCalledWith('1');
    expect(catalog.listReviews).toHaveBeenCalledWith('1', null, 20, null);
    expect(fixture.componentInstance.product()).toEqual(product);
    expect(fixture.componentInstance.reviewEdges().length).toBe(1);
  });

  it('hides the delete-review action for a non-admin user', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="delete-review-9"]')).toBeNull();
  });

  it('shows and wires the delete-review action for an admin user', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });
    catalog.deleteReview.and.returnValue(of(true));

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="delete-review-9"]');
    button.click();

    expect(catalog.deleteReview).toHaveBeenCalledWith('9');
  });
});
