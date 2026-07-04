import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RepeatDirective } from './repeat.directive';

@Component({
  imports: [RepeatDirective],
  template: `<li *appRepeat="count()">Item</li>`,
})
class HostComponent {
  readonly count = signal(3);
}

describe('RepeatDirective', () => {
  it('renders the template once per repeat count and re-renders on change', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(3);

    fixture.componentInstance.count.set(5);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(5);
  });
});
