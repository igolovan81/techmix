import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { RoutingDemo } from './routing-demo';
import { SelectionStore } from './selection-store';

describe('RoutingDemo', () => {
  it('selects the item and navigates to its detail route', () => {
    const navigateSpy = jasmine.createSpy('navigate');

    TestBed.configureTestingModule({
      imports: [RoutingDemo],
      providers: [
        { provide: Router, useValue: { navigate: navigateSpy } },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });

    const fixture = TestBed.createComponent(RoutingDemo);
    fixture.detectChanges();

    const store = TestBed.inject(SelectionStore);
    fixture.componentInstance.select(1);

    expect(store.hasSelection()).toBe(true);
    expect(navigateSpy).toHaveBeenCalledWith(['item', 1], { relativeTo: jasmine.any(Object) });
  });
});
