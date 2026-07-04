import { Component, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-animations-demo',
  imports: [MatButtonModule, MatListModule],
  template: `
    <button mat-raised-button color="primary" (click)="add()" data-testid="add-button">Add item</button>
    <button mat-stroked-button (click)="removeLast()" data-testid="remove-button">Remove last</button>
    <mat-nav-list data-testid="animated-list">
      @for (item of items(); track item) {
        <mat-list-item animate.enter="fade-in" animate.leave="fade-out">{{ item }}</mat-list-item>
      }
    </mat-nav-list>
  `,
  styles: `
    .fade-in {
      animation: fade-slide-in 200ms ease-out;
    }
    .fade-out {
      animation: fade-slide-out 150ms ease-in;
    }
    @keyframes fade-slide-in {
      from {
        opacity: 0;
        transform: translateX(-16px);
      }
    }
    @keyframes fade-slide-out {
      to {
        opacity: 0;
        transform: translateX(16px);
      }
    }
  `,
})
export class AnimationsDemo {
  private counter = 0;
  readonly items = signal<string[]>([]);

  add(): void {
    this.counter += 1;
    this.items.update((current) => [...current, `Item ${this.counter}`]);
  }

  removeLast(): void {
    this.items.update((current) => current.slice(0, -1));
  }
}
