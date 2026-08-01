import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { CategoryService } from './category.service';
import { Category, Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';

describe('CategoryService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: CategoryService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(CategoryService);
  });

  it('listCategories maps the root categories connection', (done) => {
    const connection: Connection<Category> = { ...emptyConnection<Category>(), totalCount: 3 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { categories: connection } }) } as never);

    service.listCategories(50, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('listChildren maps the nested children connection, defaulting to empty when the category is missing', (done) => {
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { category: null } }) } as never);

    service.listChildren('1', 50, null).subscribe((result) => {
      expect(result).toEqual(emptyConnection<Category>());
      done();
    });
  });

  it('listProducts maps the nested products connection', (done) => {
    const connection: Connection<Product> = { ...emptyConnection<Product>(), totalCount: 5 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { category: { id: '1', products: connection } } }) } as never);

    service.listProducts('1', 10, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });
});
