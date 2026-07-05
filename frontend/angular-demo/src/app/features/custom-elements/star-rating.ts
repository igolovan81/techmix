import { Component, input, numberAttribute } from '@angular/core';

@Component({
  selector: 'app-star-rating',
  template: `
    @for (star of stars; track star) {
      <span data-testid="star">{{ star <= rating() ? '★' : '☆' }}</span>
    }
  `,
})
export class StarRating {
  readonly rating = input(0, { transform: numberAttribute });
  readonly stars = [1, 2, 3, 4, 5];
}
