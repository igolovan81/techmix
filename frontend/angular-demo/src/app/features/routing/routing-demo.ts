import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { SelectionStore } from './selection-store';
import { DEMO_ITEMS } from './demo-items';

@Component({
  selector: 'app-routing-demo',
  imports: [MatListModule, RouterOutlet],
  template: `
    <mat-nav-list>
      @for (item of items; track item.id) {
        <a mat-list-item (click)="select(item.id)" [attr.data-testid]="'route-item-' + item.id">
          {{ item.name }}
        </a>
      }
    </mat-nav-list>
    <router-outlet />
  `,
})
export class RoutingDemo {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly selectionStore = inject(SelectionStore);
  readonly items = DEMO_ITEMS;

  select(id: number): void {
    this.selectionStore.select(id);
    this.router.navigate(['item', id], { relativeTo: this.route });
  }
}
