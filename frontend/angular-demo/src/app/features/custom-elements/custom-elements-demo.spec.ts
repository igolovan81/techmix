import { TestBed } from '@angular/core/testing';
import { CustomElementsDemo } from './custom-elements-demo';

describe('CustomElementsDemo', () => {
  beforeEach(() => {
    // Angular Elements' generated class captures the EnvironmentInjector at
    // registration time. TestBed destroys that injector after each test by
    // default, which breaks any later test in this file that instantiates
    // <app-star-rating> (NG0205: Injector has already been destroyed).
    TestBed.configureTestingModule({ teardown: { destroyAfterEach: false } });
  });

  it('renders the declaratively-bound rating and updates via increment/reset', async () => {
    const fixture = TestBed.createComponent(CustomElementsDemo);
    await fixture.whenStable();

    const declarative = fixture.nativeElement.querySelector('[data-testid="declarative-rating"]');
    expect(declarative.getAttribute('rating')).toBe('2');

    fixture.componentInstance.increment();
    await fixture.whenStable();
    expect(declarative.getAttribute('rating')).toBe('3');

    fixture.componentInstance.reset();
    await fixture.whenStable();
    expect(declarative.getAttribute('rating')).toBe('0');
  });

  it('appends a new app-star-rating element on "create imperatively"', async () => {
    const fixture = TestBed.createComponent(CustomElementsDemo);
    await fixture.whenStable();

    const host = fixture.nativeElement.querySelector('[data-testid="imperative-host"]');
    expect(host.children.length).toBe(0);

    fixture.componentInstance.createImperatively();
    await fixture.whenStable();

    expect(host.children.length).toBe(1);
    expect(host.children[0].tagName.toLowerCase()).toBe('app-star-rating');
  });
});
