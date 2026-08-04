import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CategoryTree } from './category-tree';
import { CategoryService } from './category.service';
import { Connection, Category } from '../../core/graphql/graphql.models';

describe('CategoryTree', () => {
  it('loads and shows only the root categories, filtering out children returned in the same flat page', () => {
    const service = jasmine.createSpyObj<CategoryService>(['listCategories']);
    const root: Category = { id: '1', name: 'Electronics', parent: null };
    const child: Category = { id: '11', name: 'Electronics 1', parent: { id: '1', name: 'Electronics' } };
    const connection: Connection<Category> = {
      edges: [
        { cursor: 'c1', node: root },
        { cursor: 'c2', node: child },
      ],
      pageInfo: { hasNextPage: false, endCursor: 'c2' },
      totalCount: 2,
    };
    service.listCategories.and.returnValue(of(connection));
    TestBed.configureTestingModule({
      imports: [CategoryTree],
      providers: [provideNoopAnimations(), { provide: CategoryService, useValue: service }],
    });

    const fixture = TestBed.createComponent(CategoryTree);
    fixture.detectChanges();

    expect(service.listCategories).toHaveBeenCalledWith(50, null);
    expect(fixture.componentInstance.rootCategories()).toEqual([root]);
    expect(fixture.nativeElement.querySelectorAll('app-category-node').length).toBe(1);
  });
});
