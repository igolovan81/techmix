import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { SelectionStore } from './selection-store';

export const hasSelectionGuard: CanActivateFn = () => {
  const store = inject(SelectionStore);
  return store.hasSelection();
};
