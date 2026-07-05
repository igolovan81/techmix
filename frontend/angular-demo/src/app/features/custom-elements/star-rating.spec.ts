import { TestBed } from '@angular/core/testing';
import { StarRating } from './star-rating';

describe('StarRating', () => {
  it('renders filled and empty stars according to rating()', () => {
    const fixture = TestBed.createComponent(StarRating);
    fixture.componentRef.setInput('rating', 3);
    fixture.detectChanges();

    const stars = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="star"]')).map(
      (el) => (el as HTMLElement).textContent,
    );
    expect(stars).toEqual(['★', '★', '★', '☆', '☆']);
  });

  it('defaults to 0 (all empty stars)', () => {
    const fixture = TestBed.createComponent(StarRating);
    fixture.detectChanges();

    const stars = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="star"]')).map(
      (el) => (el as HTMLElement).textContent,
    );
    expect(stars).toEqual(['☆', '☆', '☆', '☆', '☆']);
  });
});
