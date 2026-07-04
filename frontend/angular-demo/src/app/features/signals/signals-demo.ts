import { Component, computed, effect, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-signals-demo',
  imports: [MatCardModule, MatButtonModule],
  template: `
    <mat-card>
      <mat-card-title>Signals</mat-card-title>
      <mat-card-content>
        <p data-testid="count">Count: {{ count() }}</p>
        <p data-testid="doubled">Doubled (computed): {{ doubled() }}</p>
        <p data-testid="history-length">Effect history entries: {{ history().length }}</p>
        <button mat-raised-button color="primary" (click)="increment()" data-testid="increment">
          Increment
        </button>
        <button mat-stroked-button (click)="reset()" data-testid="reset">Reset</button>
      </mat-card-content>
    </mat-card>
  `,
})
export class SignalsDemo {
  readonly count = signal(0);
  readonly doubled = computed(() => this.count() * 2);
  readonly history = signal<number[]>([]);

  constructor() {
    effect(() => {
      const value = this.count();
      this.history.update((entries) => [...entries, value]);
    });
  }

  increment(): void {
    this.count.update((value) => value + 1);
  }

  reset(): void {
    this.count.set(0);
  }
}
