import { Component, inject, signal } from '@angular/core';
import { Category } from '../../core/graphql/graphql.models';
import { CategoryService } from './category.service';
import { CategoryNode } from './category-node';

@Component({
  selector: 'app-category-tree',
  imports: [CategoryNode],
  templateUrl: './category-tree.html',
  styleUrl: './category-tree.scss',
})
export class CategoryTree {
  private readonly categoryService = inject(CategoryService);

  readonly rootCategories = signal<Category[]>([]);

  constructor() {
    this.categoryService.listCategories(50, null).subscribe((connection) => {
      const roots = connection.edges.filter((edge) => edge.node.parent == null).map((edge) => edge.node);
      this.rootCategories.set(roots);
    });
  }
}
