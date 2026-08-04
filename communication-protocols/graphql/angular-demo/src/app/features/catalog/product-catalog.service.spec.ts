import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ProductCatalogService } from './product-catalog.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductCatalogService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: ProductCatalogService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery', 'mutate']);
    TestBed.configureTestingModule({
      providers: [{ provide: Apollo, useValue: apollo }, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductCatalogService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listProducts maps the products connection', (done) => {
    const connection: Connection<Product> = { ...emptyConnection<Product>(), totalCount: 1 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { products: connection } }) } as never);

    service.listProducts(null, 20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('getProduct maps a single product', (done) => {
    const product: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { product } }) } as never);

    service.getProduct('1').subscribe((result) => {
      expect(result).toEqual(product);
      done();
    });
  });

  it('listReviews maps the nested reviews connection, defaulting to empty when the product is missing', (done) => {
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { product: null } }) } as never);

    service.listReviews('1', null, 20, null).subscribe((result) => {
      expect(result).toEqual(emptyConnection<Review>());
      done();
    });
  });

  it('addReview maps the created review', (done) => {
    const review: Review = { id: '9', productId: '1', author: { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' }, rating: 5, comment: 'Great' };
    apollo.mutate.and.returnValue(of({ data: { addReview: review } }) as never);

    service.addReview({ productId: '1', rating: 5, comment: 'Great' }).subscribe((result) => {
      expect(result).toEqual(review);
      done();
    });
  });

  it('deleteReview maps the boolean result', (done) => {
    apollo.mutate.and.returnValue(of({ data: { deleteReview: true } }) as never);

    service.deleteReview('9').subscribe((result) => {
      expect(result).toBe(true);
      done();
    });
  });

  it('uploadProductImage posts multipart form data to the REST endpoint', (done) => {
    const file = new File(['abc'], 'image.png', { type: 'image/png' });

    service.uploadProductImage('1', file).subscribe(() => done());

    const req = httpMock.expectOne('/api/products/1/image');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(null);
  });
});
