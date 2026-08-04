import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CategoryNode } from './category-node';
import { CategoryService } from './category.service';
import { Category, Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';

describe('CategoryNode', () => {
  let service: jasmine.SpyObj<CategoryService>;
  const category: Category = { id: '1', name: 'Electronics' };

  beforeEach(() => {
    service = jasmine.createSpyObj<CategoryService>(['listChildren', 'listProducts']);
    TestBed.configureTestingModule({
      imports: [CategoryNode],
      providers: [provideNoopAnimations(), { provide: CategoryService, useValue: service }],
    });
  });

  it('loads children lazily the first time it is expanded', () => {
    const children: Connection<Category> = { ...emptyConnection<Category>(), edges: [{ cursor: 'c', node: { id: '2', name: 'Audio' } }] };
    service.listChildren.and.returnValue(of(children));

    const fixture = TestBed.createComponent(CategoryNode);
    fixture.componentRef.setInput('category', category);
    fixture.detectChanges();

    fixture.componentInstance.toggleExpanded();

    expect(service.listChildren).toHaveBeenCalledWith('1', 50, null);
    expect(fixture.componentInstance.children()).toEqual(children.edges.map((edge) => edge.node));
  });

  it('loads products lazily the first time they are shown', () => {
    const products: Connection<Product> = { ...emptyConnection<Product>(), edges: [{ cursor: 'p', node: { id: '9', name: 'Widget', priceCents: 500, stockQty: 1 } }] };
    service.listProducts.and.returnValue(of(products));

    const fixture = TestBed.createComponent(CategoryNode);
    fixture.componentRef.setInput('category', category);
    fixture.detectChanges();

    fixture.componentInstance.toggleProducts();

    expect(service.listProducts).toHaveBeenCalledWith('1', 10, null);
    expect(fixture.componentInstance.products()).toEqual(products.edges.map((edge) => edge.node));
  });
});
