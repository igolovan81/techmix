import {
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  ElementRef,
  EnvironmentInjector,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { registerStarRatingElement } from './register-star-rating-element';

@Component({
  selector: 'app-custom-elements-demo',
  imports: [MatCardModule, MatButtonModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <mat-card>
      <mat-card-title>Declarative</mat-card-title>
      <mat-card-content>
        <app-star-rating [attr.rating]="ratingValue()" data-testid="declarative-rating" />
        <button mat-raised-button color="primary" (click)="increment()" data-testid="increment-rating">
          Increment
        </button>
        <button mat-stroked-button (click)="reset()" data-testid="reset-rating">Reset</button>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-title>Imperative</mat-card-title>
      <mat-card-content>
        <button
          mat-raised-button
          color="primary"
          (click)="createImperatively()"
          data-testid="create-imperatively"
        >
          Create imperatively
        </button>
        <div #imperativeHost data-testid="imperative-host"></div>
      </mat-card-content>
    </mat-card>
  `,
})
export class CustomElementsDemo {
  private readonly environmentInjector = inject(EnvironmentInjector);
  private readonly imperativeHost = viewChild.required<ElementRef<HTMLDivElement>>('imperativeHost');

  readonly ratingValue = signal(2);

  constructor() {
    registerStarRatingElement(this.environmentInjector);
  }

  increment(): void {
    this.ratingValue.update((value) => Math.min(value + 1, 5));
  }

  reset(): void {
    this.ratingValue.set(0);
  }

  createImperatively(): void {
    const element = document.createElement('app-star-rating') as HTMLElement & { rating: number };
    element.rating = 3;
    element.setAttribute('data-testid', 'imperative-rating');
    this.imperativeHost().nativeElement.appendChild(element);
  }
}
