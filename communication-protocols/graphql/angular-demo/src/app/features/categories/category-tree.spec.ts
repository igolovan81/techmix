import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CategoryTree } from './category-tree';
import { CategoryService } from './category.service';
import { Connection, Category } from '../../core/graphql/graphql.models';

describe('CategoryTree', () => {
  it('loads the root categories on init', () => {
    const service = jasmine.createSpyObj<CategoryService>(['listCategories']);
    const roots: Connection<Category> = {
      edges: [{ cursor: 'c', node: { id: '1', name: 'Electronics' } }],
      pageInfo: { hasNextPage: false, endCursor: 'c' },
      totalCount: 1,
    };
    service.listCategories.and.returnValue(of(roots));
    TestBed.configureTestingModule({
      imports: [CategoryTree],
      providers: [provideNoopAnimations(), { provide: CategoryService, useValue: service }],
    });

    const fixture = TestBed.createComponent(CategoryTree);
    fixture.detectChanges();

    expect(service.listCategories).toHaveBeenCalledWith(50, null);
    expect(fixture.componentInstance.rootCategories()).toEqual([{ id: '1', name: 'Electronics' }]);
    expect(fixture.nativeElement.querySelectorAll('app-category-node').length).toBe(1);
  });
});
