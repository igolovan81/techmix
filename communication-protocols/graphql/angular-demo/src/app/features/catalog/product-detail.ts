import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { Edge, PageInfo, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';
import { AuthService } from '../../core/auth/auth.service';
import { ProductCatalogService } from './product-catalog.service';
import { AddReviewDialog } from './add-review-dialog';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-product-detail',
  imports: [DecimalPipe, MatCardModule, MatButtonModule, MatChipsModule, ConnectionPaginator],
  templateUrl: './product-detail.html',
})
export class ProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(ProductCatalogService);
  private readonly dialog = inject(MatDialog);
  protected readonly authService = inject(AuthService);

  private readonly productId = this.route.snapshot.paramMap.get('id')!;

  readonly product = signal<Product | null>(null);
  readonly reviewEdges = signal<Edge<Review>[]>([]);
  readonly reviewPageInfo = signal<PageInfo>(emptyConnection<Review>().pageInfo);
  readonly reviewTotalCount = signal(0);

  constructor() {
    this.catalog.getProduct(this.productId).subscribe((product) => this.product.set(product));
    this.loadReviews(null);
  }

  loadMoreReviews(): void {
    this.loadReviews(this.reviewPageInfo().endCursor);
  }

  openAddReviewDialog(): void {
    this.dialog
      .open(AddReviewDialog)
      .afterClosed()
      .subscribe((result: { rating: number; comment: string | null } | undefined) => {
        if (!result) {
          return;
        }
        this.catalog.addReview({ productId: this.productId, rating: result.rating, comment: result.comment ?? undefined }).subscribe((review) => {
          this.reviewEdges.set([{ cursor: review.id, node: review }, ...this.reviewEdges()]);
          this.reviewTotalCount.set(this.reviewTotalCount() + 1);
        });
      });
  }

  deleteReview(id: string): void {
    this.catalog.deleteReview(id).subscribe(() => {
      this.reviewEdges.set(this.reviewEdges().filter((edge) => edge.node.id !== id));
      this.reviewTotalCount.set(this.reviewTotalCount() - 1);
    });
  }

  private loadReviews(after: string | null): void {
    this.catalog.listReviews(this.productId, null, PAGE_SIZE, after).subscribe((connection) => {
      this.reviewEdges.set([...this.reviewEdges(), ...connection.edges]);
      this.reviewPageInfo.set(connection.pageInfo);
      this.reviewTotalCount.set(connection.totalCount);
    });
  }
}
