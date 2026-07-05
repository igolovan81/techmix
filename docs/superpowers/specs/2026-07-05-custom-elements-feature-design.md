# Custom Elements Feature Design

**Date:** 2026-07-05
**Status:** Approved

## Overview

A 10th capability page for `frontend/angular-demo/`: `/custom-elements`, demonstrating Angular Elements (`@angular/elements`). Wraps a small presentational `StarRating` component as a native Web Component (`<app-star-rating>`) registered through the browser's own `CustomElementRegistry` (`customElements.define`), then shows it driven two ways — declaratively through an Angular template binding, and imperatively through raw DOM APIs — to prove the element genuinely works independent of Angular's own template/change-detection machinery, not just inside it.

Confirmed via a throwaway Karma/ChromeHeadless experiment before this design was finalized: Angular's signal-based `input()` works with `createCustomElement()` with no extra wiring — both `setAttribute()` and direct JS-property assignment correctly flow into the signal. The one real gotcha: attribute values arrive as strings, so a numeric input needs a `transform` (`numberAttribute`, from `@angular/core`) to coerce it — this is unchanged behavior from classic `@Input()` and isn't specific to signal inputs.

## Repository structure

```
frontend/angular-demo/src/app/features/custom-elements/
├── star-rating.ts                      (presentational component, signal input w/ numberAttribute transform)
├── star-rating.spec.ts
├── register-star-rating-element.ts     (createCustomElement + customElements.define, idempotent)
├── register-star-rating-element.spec.ts
├── custom-elements-demo.ts             (route component: declarative + imperative usage)
└── custom-elements-demo.spec.ts

frontend/angular-demo/e2e/
└── custom-elements.spec.ts
```

## Component design

**`StarRating`** (`src/app/features/custom-elements/star-rating.ts`):
- `readonly rating = input(0, { transform: numberAttribute });` — 0–5.
- Template renders 5 characters, ★ for `i <= rating()`, ☆ otherwise (`@for` over a fixed `[1,2,3,4,5]` array).
- Ordinary standalone Angular component — testable directly like any other component in this codebase, independent of the custom-element wrapper.

**`registerStarRatingElement(injector: Injector)`** (`register-star-rating-element.ts`):
- `if (!customElements.get('app-star-rating')) customElements.define('app-star-rating', createCustomElement(StarRating, { injector }));`
- Idempotent by construction — safe to call every time the route is entered/re-entered.
- Kept feature-local (called from `CustomElementsDemo`'s constructor via `inject(Injector)`), not registered globally in `main.ts` — consistent with every other feature here being self-contained and lazy-loaded; the custom element only needs to exist once its own route has been visited.

**`CustomElementsDemo`** (route component, `/custom-elements`):
- Calls `registerStarRatingElement(inject(Injector))` once in its constructor.
- `schemas: [CUSTOM_ELEMENTS_SCHEMA]` on the component decorator (required — Angular's template compiler doesn't know `<app-star-rating>`).
- **Declarative section:** `<app-star-rating [attr.rating]="ratingValue()">` bound to a local signal, with an Increment/Reset control identical in spirit to the Signals page — proves Angular's own binding system can drive a native custom element like any other DOM node.
- **Imperative section:** a "Create imperatively" button whose click handler does `document.createElement('app-star-rating')`, sets `el.rating = 3` as a plain JS property (not `setAttribute`) — a fixed representative value — and appends it into a plain host `<div #imperativeHost>` obtained via `viewChild`/`ElementRef`. Each click appends one more widget (stacking), so the host can end up with several `<app-star-rating rating="3">` nodes — proves the element works through the browser's native `CustomElementRegistry`, with zero Angular template involvement.

## Non-goals

- No global/eager registration in `main.ts` — stays feature-local per above.
- No Shadow DOM encapsulation concerns — `createCustomElement()` renders in light DOM by default, which is sufficient for this demo and keeps Material's global theme CSS applying normally.
- No additional npm packages beyond `@angular/elements@21.2.17` (matches the rest of the app's pinned Angular version).

## Testing strategy

- **Unit (Jasmine/Karma):**
  - `star-rating.spec.ts` — renders correct ★/☆ counts for a few `rating` values, directly against the Angular component (bypassing the custom-element wrapper).
  - `register-star-rating-element.spec.ts` — after calling `registerStarRatingElement(injector)`, `customElements.get('app-star-rating')` is a defined constructor; calling it twice does not throw.
  - `custom-elements-demo.spec.ts` — component creates; the declaratively-bound widget renders the expected stars (via `await fixture.whenStable()`, per this project's zoneless-testing convention); clicking "Create imperatively" appends a new `<app-star-rating>` node into the host container with the expected rendered rating.
- **E2E (Playwright, `e2e/custom-elements.spec.ts`):** navigate to `/custom-elements`, assert the declarative widget's star text, click "Create imperatively", assert a second widget appears with the correct rating — the one path that exercises the full native `CustomElementRegistry` round-trip in a real browser end to end.

## Nav & routing

- New `NAV_ITEMS` entry: `{ path: 'custom-elements', label: 'Custom Elements' }`, appended after `animations` (10th and last).
- New lazy route: `{ path: 'custom-elements', loadComponent: () => import('./features/custom-elements/custom-elements-demo').then((m) => m.CustomElementsDemo) }`, appended to `app.routes.ts`.
- `e2e/navigation.spec.ts`'s `NAV_ITEMS` list gets the same new entry so the existing smoke test continues to cover all routes.

## Commands (unchanged)

No new commands — `npm test`, `npx playwright test`, `npm start`, `npm run build` all continue to work as documented in `frontend/angular-demo/README.md` and `CLAUDE.md`.
