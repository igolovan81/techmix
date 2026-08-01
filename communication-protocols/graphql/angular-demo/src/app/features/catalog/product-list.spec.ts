import { TestBed } from '@angular/core/testing';
import { RouterModule, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ProductList } from './product-list';
import { ProductCatalogService } from './product-catalog.service';
import { Connection, Product } from '../../core/graphql/graphql.models';

describe('ProductList', () => {
  let service: jasmine.SpyObj<ProductCatalogService>;

  const page1: Connection<Product> = {
    edges: [{ cursor: 'c1', node: { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 } }],
    pageInfo: { hasNextPage: true, endCursor: 'c1' },
    totalCount: 2,
  };
  const page2: Connection<Product> = {
    edges: [{ cursor: 'c2', node: { id: '2', name: 'Gadget', priceCents: 900, stockQty: 5 } }],
    pageInfo: { hasNextPage: false, endCursor: 'c2' },
    totalCount: 2,
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<ProductCatalogService>(['listProducts']);
    service.listProducts.and.returnValue(of(page1));
    TestBed.configureTestingModule({
      imports: [ProductList, RouterModule],
      providers: [provideRouter([]), { provide: ProductCatalogService, useValue: service }],
    });
  });

  it('loads the first page on init', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();

    expect(service.listProducts).toHaveBeenCalledWith(null, 20, null);
    expect(fixture.componentInstance.edges().length).toBe(1);
    expect(fixture.componentInstance.totalCount()).toBe(2);
  });

  it('loading more appends the next page using the current end cursor', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    service.listProducts.and.returnValue(of(page2));

    fixture.componentInstance.loadMore();

    expect(service.listProducts).toHaveBeenCalledWith(null, 20, 'c1');
    expect(fixture.componentInstance.edges().length).toBe(2);
    expect(fixture.componentInstance.pageInfo().hasNextPage).toBe(false);
  });

  it('applying a filter resets the list and re-queries with the filter', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    service.listProducts.and.returnValue(of(page2));

    fixture.componentInstance.filterForm.setValue({ nameContains: 'Gadget', minPriceCents: null, maxPriceCents: null });
    fixture.componentInstance.search();

    expect(service.listProducts).toHaveBeenCalledWith({ nameContains: 'Gadget' }, 20, null);
    expect(fixture.componentInstance.edges().length).toBe(1);
    expect(fixture.componentInstance.edges()[0].node.name).toBe('Gadget');
  });
});
