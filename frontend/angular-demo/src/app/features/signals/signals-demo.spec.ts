import { TestBed } from '@angular/core/testing';
import { SignalsDemo } from './signals-demo';

describe('SignalsDemo', () => {
  it('computes doubled from count and tracks history via effect', () => {
    const fixture = TestBed.createComponent(SignalsDemo);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.count()).toBe(0);
    expect(component.doubled()).toBe(0);
    expect(component.history()).toEqual([0]);

    component.increment();
    fixture.detectChanges();
    expect(component.count()).toBe(1);
    expect(component.doubled()).toBe(2);
    expect(component.history()).toEqual([0, 1]);

    component.reset();
    fixture.detectChanges();
    expect(component.count()).toBe(0);
    expect(component.history()).toEqual([0, 1, 0]);
  });
});
