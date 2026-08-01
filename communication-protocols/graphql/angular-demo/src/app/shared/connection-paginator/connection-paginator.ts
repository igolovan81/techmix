import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-connection-paginator',
  imports: [MatButtonModule],
  templateUrl: './connection-paginator.html',
})
export class ConnectionPaginator {
  readonly hasNextPage = input.required<boolean>();
  readonly totalCount = input.required<number>();
  readonly loadedCount = input.required<number>();
  readonly loading = input(false);
  readonly loadMore = output<void>();
}
