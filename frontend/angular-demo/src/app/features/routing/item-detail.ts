import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { MatCardModule } from '@angular/material/card';
import { DemoRoutingItem } from './demo-items';

@Component({
  selector: 'app-item-detail',
  imports: [MatCardModule],
  template: `
    @if (item(); as value) {
      <mat-card data-testid="item-detail">
        <mat-card-title>{{ value.name }}</mat-card-title>
        <mat-card-content>{{ value.description }}</mat-card-content>
      </mat-card>
    }
  `,
})
export class ItemDetail {
  private readonly route = inject(ActivatedRoute);
  readonly item = toSignal(this.route.data.pipe(map((data) => data['item'] as DemoRoutingItem)));
}
