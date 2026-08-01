import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';
import { Product, Review } from '../../core/graphql/graphql.models';
import { ProductCatalogService } from '../catalog/product-catalog.service';
import { LiveReviewsService } from './live-reviews.service';

@Component({
  selector: 'app-live-reviews',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, MatListModule],
  templateUrl: './live-reviews.html',
})
export class LiveReviews implements OnDestroy {
  private readonly liveReviewsService = inject(LiveReviewsService);
  private readonly catalog = inject(ProductCatalogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly productFilter = this.fb.nonNullable.control<string | null>(null);
  readonly products = signal<Product[]>([]);
  readonly feed = signal<Review[]>([]);

  private subscription: Subscription | null = null;

  constructor() {
    this.catalog.listProducts(null, 50, null).subscribe((connection) => {
      this.products.set(connection.edges.map((edge) => edge.node));
    });
    this.selectProduct(null);
  }

  selectProduct(productId: string | null): void {
    this.productFilter.setValue(productId);
    this.subscription?.unsubscribe();
    this.subscription = this.liveReviewsService.subscribeToReviewAdded(productId).subscribe((review) => {
      this.feed.set([review, ...this.feed()]);
      this.snackBar.open(`New review: ${review.rating}/5`, 'Dismiss', { duration: 3000 });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
