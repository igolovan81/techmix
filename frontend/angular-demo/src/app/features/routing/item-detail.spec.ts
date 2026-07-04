import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ItemDetail } from './item-detail';
import { DEMO_ITEMS } from './demo-items';

describe('ItemDetail', () => {
  it('renders the item resolved into route.data', () => {
    TestBed.configureTestingModule({
      imports: [ItemDetail],
      providers: [{ provide: ActivatedRoute, useValue: { data: of({ item: DEMO_ITEMS[0] }) } }],
    });

    const fixture = TestBed.createComponent(ItemDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(DEMO_ITEMS[0].name);
    expect(fixture.nativeElement.textContent).toContain(DEMO_ITEMS[0].description);
  });
});
