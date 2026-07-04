import { TestBed } from '@angular/core/testing';
import { ComponentCommunicationDemo } from './component-communication-demo';

describe('ComponentCommunicationDemo', () => {
  it('reflects the child model() and counts dismiss events', () => {
    const fixture = TestBed.createComponent(ComponentCommunicationDemo);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.liked()).toBe(false);
    expect(component.dismissCount()).toBe(0);

    component.onDismissed();
    expect(component.dismissCount()).toBe(1);
  });
});
