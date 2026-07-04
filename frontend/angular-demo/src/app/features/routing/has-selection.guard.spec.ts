import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { hasSelectionGuard } from './has-selection.guard';
import { SelectionStore } from './selection-store';

describe('hasSelectionGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  it('blocks activation until a selection has been made', () => {
    TestBed.configureTestingModule({});
    const store = TestBed.inject(SelectionStore);

    const before = TestBed.runInInjectionContext(() => hasSelectionGuard(route, state));
    expect(before).toBe(false);

    store.select(1);

    const after = TestBed.runInInjectionContext(() => hasSelectionGuard(route, state));
    expect(after).toBe(true);
  });
});
