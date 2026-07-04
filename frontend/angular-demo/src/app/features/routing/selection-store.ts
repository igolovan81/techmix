import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SelectionStore {
  private readonly selectedId = signal<number | null>(null);

  select(id: number): void {
    this.selectedId.set(id);
  }

  hasSelection(): boolean {
    return this.selectedId() !== null;
  }
}
