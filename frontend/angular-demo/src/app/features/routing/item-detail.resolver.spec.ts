import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, convertToParamMap } from '@angular/router';
import { itemDetailResolver } from './item-detail.resolver';
import { DEMO_ITEMS } from './demo-items';

describe('itemDetailResolver', () => {
  it('resolves the item matching the :id route param', () => {
    const snapshot = { paramMap: convertToParamMap({ id: '2' }) } as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      itemDetailResolver(snapshot, {} as RouterStateSnapshot),
    );

    expect(result).toEqual(DEMO_ITEMS[1]);
  });
});
