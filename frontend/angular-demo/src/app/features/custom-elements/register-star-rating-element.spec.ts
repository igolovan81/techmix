import { TestBed } from '@angular/core/testing';
import { Injector } from '@angular/core';
import { registerStarRatingElement } from './register-star-rating-element';

describe('registerStarRatingElement', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('registers app-star-rating on the CustomElementRegistry', () => {
    const injector = TestBed.inject(Injector);

    registerStarRatingElement(injector);

    expect(customElements.get('app-star-rating')).toBeDefined();
  });

  it('is idempotent when called more than once', () => {
    const injector = TestBed.inject(Injector);

    expect(() => {
      registerStarRatingElement(injector);
      registerStarRatingElement(injector);
    }).not.toThrow();
  });
});
