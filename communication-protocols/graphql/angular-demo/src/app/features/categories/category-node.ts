import { Component, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { Category, Product } from '../../core/graphql/graphql.models';
import { CategoryService } from './category.service';

@Component({
  selector: 'app-category-node',
  imports: [DecimalPipe, MatButtonModule, MatListModule, CategoryNode],
  templateUrl: './category-node.html',
})
export class CategoryNode {
  private readonly categoryService = inject(CategoryService);

  readonly category = input.required<Category>();

  readonly expanded = signal(false);
  readonly children = signal<Category[] | null>(null);

  readonly showingProducts = signal(false);
  readonly products = signal<Product[] | null>(null);

  toggleExpanded(): void {
    this.expanded.set(!this.expanded());
    if (this.expanded() && this.children() === null) {
      this.categoryService.listChildren(this.category().id, 50, null).subscribe((connection) => {
        this.children.set(connection.edges.map((edge) => edge.node));
      });
    }
  }

  toggleProducts(): void {
    this.showingProducts.set(!this.showingProducts());
    if (this.showingProducts() && this.products() === null) {
      this.categoryService.listProducts(this.category().id, 10, null).subscribe((connection) => {
        this.products.set(connection.edges.map((edge) => edge.node));
      });
    }
  }
}
