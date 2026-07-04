import { TestBed } from '@angular/core/testing';
import { PipesDemo } from './pipes-demo';

describe('PipesDemo', () => {
  it('renders truncated and uppercased text', () => {
    const fixture = TestBed.createComponent(PipesDemo);
    fixture.detectChanges();

    const truncated = fixture.nativeElement.querySelector('[data-testid="truncated"]').textContent;
    const uppercased = fixture.nativeElement.querySelector('[data-testid="uppercased"]').textContent;

    expect(truncated.length).toBeLessThanOrEqual(26);
    expect(uppercased).toContain('ANGULAR');
  });
});
