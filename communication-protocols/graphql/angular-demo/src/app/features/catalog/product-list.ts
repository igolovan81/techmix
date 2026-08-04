import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { Edge, PageInfo, Product, ProductFilter, emptyConnection } from '../../core/graphql/graphql.models';
import { CartService } from '../orders/cart.service';
import { ProductCatalogService } from './product-catalog.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-product-list',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    ConnectionPaginator,
  ],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly catalog = inject(ProductCatalogService);
  private readonly fb = inject(FormBuilder);
  protected readonly cartService = inject(CartService);

  readonly filterForm = this.fb.nonNullable.group({
    nameContains: this.fb.control<string | null>(null),
    minPriceCents: this.fb.control<number | null>(null),
    maxPriceCents: this.fb.control<number | null>(null),
  });

  readonly edges = signal<Edge<Product>[]>([]);
  readonly pageInfo = signal<PageInfo>(emptyConnection<Product>().pageInfo);
  readonly totalCount = signal(0);
  readonly loading = signal(false);

  constructor() {
    this.search();
  }

  search(): void {
    this.edges.set([]);
    this.loadPage(null);
  }

  loadMore(): void {
    this.loadPage(this.pageInfo().endCursor);
  }

  addToCart(product: Product, event: Event): void {
    event.stopPropagation();
    this.cartService.add(product);
  }

  private loadPage(after: string | null): void {
    this.loading.set(true);
    this.catalog.listProducts(this.currentFilter(), PAGE_SIZE, after).subscribe((connection) => {
      this.loading.set(false);
      this.edges.set([...this.edges(), ...connection.edges]);
      this.pageInfo.set(connection.pageInfo);
      this.totalCount.set(connection.totalCount);
    });
  }

  private currentFilter(): ProductFilter | null {
    const raw = this.filterForm.getRawValue();
    const filter: ProductFilter = {};
    if (raw.nameContains) {
      filter.nameContains = raw.nameContains;
    }
    if (raw.minPriceCents != null) {
      filter.minPriceCents = raw.minPriceCents;
    }
    if (raw.maxPriceCents != null) {
      filter.maxPriceCents = raw.maxPriceCents;
    }
    return Object.keys(filter).length > 0 ? filter : null;
  }
}
