import { TestBed } from '@angular/core/testing';
import { DirectivesDemo } from './directives-demo';

describe('DirectivesDemo', () => {
  it('renders repeatCount() list items', () => {
    const fixture = TestBed.createComponent(DirectivesDemo);
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('[data-testid="repeat-list"] li');
    expect(items.length).toBe(fixture.componentInstance.repeatCount());
  });
});
