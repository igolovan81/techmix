import { Component, signal } from '@angular/core';
import { ChildCard } from './child-card';

@Component({
  selector: 'app-component-communication-demo',
  imports: [ChildCard],
  template: `
    <app-child-card title="Angular Signals" [(liked)]="liked" (dismissed)="onDismissed()">
      <p card-body>This card's "liked" state is a two-way bound model() signal.</p>
      <small card-footer>Projected via the ng-content footer slot.</small>
    </app-child-card>
    <p data-testid="parent-liked">Parent sees liked = {{ liked() }}</p>
    <p data-testid="dismiss-count">Dismiss count: {{ dismissCount() }}</p>
  `,
})
export class ComponentCommunicationDemo {
  readonly liked = signal(false);
  readonly dismissCount = signal(0);

  onDismissed(): void {
    this.dismissCount.update((value) => value + 1);
  }
}
