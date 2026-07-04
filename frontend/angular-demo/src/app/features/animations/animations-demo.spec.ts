import { TestBed } from '@angular/core/testing';
import { AnimationsDemo } from './animations-demo';

describe('AnimationsDemo', () => {
  it('adds and removes items from the animated list', async () => {
    const fixture = TestBed.createComponent(AnimationsDemo);
    await fixture.whenStable();

    fixture.componentInstance.add();
    fixture.componentInstance.add();
    await fixture.whenStable();
    expect(fixture.componentInstance.items().length).toBe(2);

    fixture.componentInstance.removeLast();
    await fixture.whenStable();
    expect(fixture.componentInstance.items().length).toBe(1);
  });
});
