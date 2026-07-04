import { Component, input, model, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-child-card',
  imports: [MatCardModule, MatButtonModule],
  template: `
    <mat-card>
      <mat-card-title>{{ title() }}</mat-card-title>
      <mat-card-content>
        <ng-content select="[card-body]"></ng-content>
        <p data-testid="liked-state">Liked: {{ liked() }}</p>
      </mat-card-content>
      <mat-card-actions>
        <button mat-button (click)="toggleLiked()" data-testid="toggle-liked">Toggle Like</button>
        <button mat-button (click)="dismissed.emit()" data-testid="dismiss">Dismiss</button>
      </mat-card-actions>
      <ng-content select="[card-footer]"></ng-content>
    </mat-card>
  `,
})
export class ChildCard {
  readonly title = input.required<string>();
  readonly liked = model(false);
  readonly dismissed = output<void>();

  toggleLiked(): void {
    this.liked.update((value) => !value);
  }
}
