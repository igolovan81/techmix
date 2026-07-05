import { Injector } from '@angular/core';
import { createCustomElement } from '@angular/elements';
import { StarRating } from './star-rating';

export function registerStarRatingElement(injector: Injector): void {
  if (customElements.get('app-star-rating')) {
    return;
  }
  const StarRatingElement = createCustomElement(StarRating, { injector });
  customElements.define('app-star-rating', StarRatingElement);
}
