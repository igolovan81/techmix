import { Component, inject, signal } from '@angular/core';
import { Category } from '../../core/graphql/graphql.models';
import { CategoryService } from './category.service';
import { CategoryNode } from './category-node';

@Component({
  selector: 'app-category-tree',
  imports: [CategoryNode],
  templateUrl: './category-tree.html',
})
export class CategoryTree {
  private readonly categoryService = inject(CategoryService);

  readonly rootCategories = signal<Category[]>([]);

  constructor() {
    this.categoryService.listCategories(50, null).subscribe((connection) => {
      this.rootCategories.set(connection.edges.map((edge) => edge.node));
    });
  }
}
