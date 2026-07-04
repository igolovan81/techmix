import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FakeApiService } from './fake-api.service';

@Component({
  selector: 'app-data-fetching-demo',
  imports: [MatListModule, MatProgressSpinnerModule],
  template: `
    @if (items() === undefined) {
      <mat-spinner data-testid="loading-spinner" diameter="32"></mat-spinner>
    } @else {
      <mat-nav-list data-testid="items-list">
        @for (item of items(); track item.id) {
          <mat-list-item>{{ item.name }}</mat-list-item>
        }
      </mat-nav-list>
    }
  `,
})
export class DataFetchingDemo {
  private readonly api = inject(FakeApiService);
  readonly items = toSignal(this.api.getItems());
}
