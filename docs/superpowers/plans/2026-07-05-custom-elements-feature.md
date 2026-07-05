# Custom Elements Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 10th capability page (`/custom-elements`) to `frontend/angular-demo/` demonstrating Angular Elements — a `StarRating` component wrapped as a native Web Component (`<app-star-rating>`) via `createCustomElement()`, driven both declaratively (Angular template binding) and imperatively (raw DOM APIs) to prove it works through the browser's own `CustomElementRegistry`.

**Architecture:** One new self-contained feature folder, `src/app/features/custom-elements/`, following the existing package-per-pattern convention. Registration is feature-local (called from the route component's constructor, not globally in `main.ts`) and idempotent.

**Tech Stack:** `@angular/elements@21.2.17` (new dependency, matches the rest of the app's pinned Angular version), signal-based `input()` with a `numberAttribute` transform, Jasmine/Karma unit tests, Playwright e2e test.

## Global Constraints

- `@angular/elements` is pinned to `21.2.17` — matches every other `@angular/*` package in this app.
- Registration (`registerStarRatingElement`) must be idempotent (`customElements.get('app-star-rating')` guard) and called from the feature's own route component, never from `main.ts` or `app.config.ts` — every other feature in this app is self-contained and lazy-loaded, and this one follows the same rule.
- The numeric `rating` input must use `numberAttribute` as its `input()` transform — attribute values arrive as strings, and this project's Angular Elements experiment confirmed no automatic coercion happens otherwise.
- `CUSTOM_ELEMENTS_SCHEMA` is required on any component template that references `<app-star-rating>` directly — Angular's template compiler doesn't know the tag otherwise.
- This project is zoneless (no `zone.js`) — tests must use real `async`/`await` and `await fixture.whenStable()`, never `fakeAsync`/`tick`.
- Elements exercised by the Playwright e2e test must carry a `data-testid` attribute, per this project's existing convention.
- No Shadow DOM — `createCustomElement()`'s default light-DOM rendering is used so Angular Material's global theme CSS keeps applying and light-DOM `data-testid` queries work directly from Playwright.

---

## Task 1: `StarRating` component + `@angular/elements` dependency

**Files:**
- Modify: `frontend/angular-demo/package.json` — add `@angular/elements` dependency
- Create: `frontend/angular-demo/src/app/features/custom-elements/star-rating.ts`
- Create: `frontend/angular-demo/src/app/features/custom-elements/star-rating.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `StarRating` component (selector `app-star-rating`, `readonly rating = input(0, { transform: numberAttribute })`), each rendered star wrapped in `<span data-testid="star">` — consumed by Task 2 (`createCustomElement(StarRating, ...)`) and Task 3 (`CustomElementsDemo`'s template).

- [ ] **Step 1: Install `@angular/elements`**

```bash
cd frontend/angular-demo
npm install @angular/elements@21.2.17
```

- [ ] **Step 2: Write the failing test**

`frontend/angular-demo/src/app/features/custom-elements/star-rating.spec.ts`:

```ts
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
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './star-rating'".

- [ ] **Step 4: Implement the component**

`frontend/angular-demo/src/app/features/custom-elements/star-rating.ts`:

```ts
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
```

- [ ] **Step 5: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add StarRating component for custom elements demo"
```

---

## Task 2: `registerStarRatingElement`

**Files:**
- Create: `frontend/angular-demo/src/app/features/custom-elements/register-star-rating-element.ts`
- Create: `frontend/angular-demo/src/app/features/custom-elements/register-star-rating-element.spec.ts`

**Interfaces:**
- Consumes: `StarRating` (Task 1).
- Produces: `registerStarRatingElement(injector: Injector): void` — idempotent; registers `app-star-rating` on the browser's `CustomElementRegistry`. Consumed by Task 3 (`CustomElementsDemo`'s constructor).

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/custom-elements/register-star-rating-element.spec.ts`:

```ts
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
  // registerStarRatingElement's own guard logic. (Discovered the hard way:
  // an earlier version of this test called the real customElements.define()
  // with a short-lived TestBed Injector — whichever of these two spec files
  // happened to run first "won" the one-time registration, and the loser's
  // later attempt to render <app-star-rating> crashed with `NG0205: Injector
  // has already been destroyed` once that Injector's own test had finished.)
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './register-star-rating-element'".

- [ ] **Step 3: Implement the registration function**

`frontend/angular-demo/src/app/features/custom-elements/register-star-rating-element.ts`:

```ts
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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add idempotent app-star-rating custom element registration"
```

---

## Task 3: `CustomElementsDemo` component + route + nav entry

**Files:**
- Create: `frontend/angular-demo/src/app/features/custom-elements/custom-elements-demo.ts`
- Create: `frontend/angular-demo/src/app/features/custom-elements/custom-elements-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/nav-items.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `registerStarRatingElement` (Task 2).
- Produces: `CustomElementsDemo`, routed at `/custom-elements`; `NAV_ITEMS` gains a 10th entry `{ path: 'custom-elements', label: 'Custom Elements' }`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/custom-elements/custom-elements-demo.spec.ts`:

```ts
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './custom-elements-demo'".

- [ ] **Step 3: Implement the component**

`frontend/angular-demo/src/app/features/custom-elements/custom-elements-demo.ts`:

```ts
import {
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  ElementRef,
  EnvironmentInjector,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { registerStarRatingElement } from './register-star-rating-element';

@Component({
  selector: 'app-custom-elements-demo',
  imports: [MatCardModule, MatButtonModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <mat-card>
      <mat-card-title>Declarative</mat-card-title>
      <mat-card-content>
        <app-star-rating [attr.rating]="ratingValue()" data-testid="declarative-rating" />
        <button mat-raised-button color="primary" (click)="increment()" data-testid="increment-rating">
          Increment
        </button>
        <button mat-stroked-button (click)="reset()" data-testid="reset-rating">Reset</button>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-title>Imperative</mat-card-title>
      <mat-card-content>
        <button
          mat-raised-button
          color="primary"
          (click)="createImperatively()"
          data-testid="create-imperatively"
        >
          Create imperatively
        </button>
        <div #imperativeHost data-testid="imperative-host"></div>
      </mat-card-content>
    </mat-card>
  `,
})
export class CustomElementsDemo {
  private readonly environmentInjector = inject(EnvironmentInjector);
  private readonly imperativeHost = viewChild.required<ElementRef<HTMLDivElement>>('imperativeHost');

  readonly ratingValue = signal(2);

  constructor() {
    registerStarRatingElement(this.environmentInjector);
  }

  increment(): void {
    this.ratingValue.update((value) => Math.min(value + 1, 5));
  }

  reset(): void {
    this.ratingValue.set(0);
  }

  createImperatively(): void {
    const element = document.createElement('app-star-rating') as HTMLElement & { rating: number };
    element.rating = 3;
    element.setAttribute('data-testid', 'imperative-rating');
    this.imperativeHost().nativeElement.appendChild(element);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Add the nav entry**

In `frontend/angular-demo/src/app/nav-items.ts`, append to `NAV_ITEMS`:

```ts
  { path: 'custom-elements', label: 'Custom Elements' },
```

- [ ] **Step 6: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'custom-elements',
    loadComponent: () =>
      import('./features/custom-elements/custom-elements-demo').then((m) => m.CustomElementsDemo),
  },
```

- [ ] **Step 7: Run all tests to verify they pass**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS — `App`'s "renders one sidenav link per NAV_ITEMS entry" test now expects 10 links and still passes since it reads `NAV_ITEMS.length` dynamically.

- [ ] **Step 8: Manually verify in the browser**

```bash
npm start
```

Open `http://localhost:4201/custom-elements`, confirm: the declarative widget shows 2 filled stars, clicking Increment/Reset updates it, and clicking "Create imperatively" appends a 3-filled-star widget below the button each time it's clicked. Stop the server (Ctrl+C).

- [ ] **Step 9: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add custom elements capability demo"
```

---

## Task 4: E2E coverage + navigation smoke test update

**Files:**
- Create: `frontend/angular-demo/e2e/custom-elements.spec.ts`
- Modify: `frontend/angular-demo/e2e/navigation.spec.ts`

**Interfaces:**
- Consumes: `data-testid` attributes from `StarRating` (Task 1: `star`) and `CustomElementsDemo` (Task 3: `declarative-rating`, `increment-rating`, `create-imperatively`, `imperative-rating`).

- [ ] **Step 1: Add the nav entry to the smoke test**

In `frontend/angular-demo/e2e/navigation.spec.ts`, append to the `NAV_ITEMS` array:

```ts
  { path: 'custom-elements', label: 'Custom Elements' },
```

- [ ] **Step 2: Write the e2e test**

`frontend/angular-demo/e2e/custom-elements.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

test('declarative binding and imperative creation both render app-star-rating widgets', async ({ page }) => {
  await page.goto('/custom-elements');

  const declarative = page.getByTestId('declarative-rating');
  await expect(declarative.getByTestId('star').nth(0)).toHaveText('★');
  await expect(declarative.getByTestId('star').nth(1)).toHaveText('★');
  await expect(declarative.getByTestId('star').nth(2)).toHaveText('☆');

  await page.getByTestId('increment-rating').click();
  await expect(declarative.getByTestId('star').nth(2)).toHaveText('★');
  await expect(declarative.getByTestId('star').nth(3)).toHaveText('☆');

  await page.getByTestId('create-imperatively').click();
  const imperative = page.getByTestId('imperative-rating');
  await expect(imperative.getByTestId('star').nth(0)).toHaveText('★');
  await expect(imperative.getByTestId('star').nth(2)).toHaveText('★');
  await expect(imperative.getByTestId('star').nth(3)).toHaveText('☆');
});
```

- [ ] **Step 3: Run the e2e suite to verify it passes**

```bash
cd frontend/angular-demo
npx playwright test
```

Expected: all e2e specs pass, including `custom-elements.spec.ts` and the updated `navigation.spec.ts` (now covering 10 routes).

- [ ] **Step 4: Run the full verification suite**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npx playwright test
```

Expected: all unit tests pass, the production build succeeds, all e2e tests pass.

- [ ] **Step 5: Update README and CLAUDE.md feature tables**

In `frontend/angular-demo/README.md`, append to the feature tour table:

```markdown
| `/custom-elements` | `@angular/elements` — native Web Component via `createCustomElement`, driven declaratively and imperatively |
```

In `/Users/admin/IdeaProjects/private/techmix-copy/CLAUDE.md`, no changes needed — the `frontend/angular-demo/` row already describes the app generally and doesn't enumerate individual features.

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "test(frontend): add e2e coverage for the custom elements demo"
```
