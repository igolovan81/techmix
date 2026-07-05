import { TestBed } from '@angular/core/testing';
import { Injector } from '@angular/core';
import { registerStarRatingElement } from './register-star-rating-element';

describe('registerStarRatingElement', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  // Spy on the global CustomElementRegistry instead of calling it for real:
  // customElements.define() can only run once per tag for the entire browser
  // page's lifetime, and the constructor it registers closes over whatever
  // Injector was live at that moment. custom-elements-demo.spec.ts performs
  // the one real registration for this app; this file only verifies
  // registerStarRatingElement's own guard logic.
  it('defines app-star-rating when not already registered', () => {
    spyOn(customElements, 'get').and.returnValue(undefined);
    const defineSpy = spyOn(customElements, 'define');
    const injector = TestBed.inject(Injector);

    registerStarRatingElement(injector);

    expect(defineSpy).toHaveBeenCalledWith('app-star-rating', jasmine.any(Function));
  });

  it('does not redefine app-star-rating when already registered', () => {
    spyOn(customElements, 'get').and.returnValue(class extends HTMLElement {} as CustomElementConstructor);
    const defineSpy = spyOn(customElements, 'define');
    const injector = TestBed.inject(Injector);

    registerStarRatingElement(injector);

    expect(defineSpy).not.toHaveBeenCalled();
  });
});
