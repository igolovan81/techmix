import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { DeferredLoadingDemo } from './deferred-loading-demo';

describe('DeferredLoadingDemo', () => {
  it('renders the placeholder, then the widget once the defer block completes', async () => {
    TestBed.configureTestingModule({
      imports: [DeferredLoadingDemo],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    await TestBed.compileComponents();
    const fixture = TestBed.createComponent(DeferredLoadingDemo);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="defer-placeholder"]')).toBeTruthy();

    const [deferBlockFixture] = await fixture.getDeferBlocks();
    await deferBlockFixture.render(DeferBlockState.Complete);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="heavy-widget"]')).toBeTruthy();
  });
});
