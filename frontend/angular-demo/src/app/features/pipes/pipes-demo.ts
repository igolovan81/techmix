import { Component, signal } from '@angular/core';
import { DatePipe, UpperCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { TruncatePipe } from './truncate.pipe';

@Component({
  selector: 'app-pipes-demo',
  imports: [MatCardModule, DatePipe, UpperCasePipe, TruncatePipe],
  template: `
    <mat-card>
      <p data-testid="truncated">{{ longText() | truncate: 24 }}</p>
      <p data-testid="uppercased">{{ shortText() | uppercase }}</p>
      <p data-testid="dated">{{ now | date: 'yyyy-MM-dd' }}</p>
    </mat-card>
  `,
})
export class PipesDemo {
  readonly longText = signal('Angular pipes transform displayed values declaratively.');
  readonly shortText = signal('angular');
  readonly now = new Date(2026, 0, 1);
}
