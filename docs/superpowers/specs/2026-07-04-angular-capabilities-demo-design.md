# Angular Capabilities Demo Design

**Date:** 2026-07-04
**Status:** Approved

## Overview

A new standalone Angular application at `frontend/angular-demo/`, sibling to the existing `frontend/angular/` (which remains the untouched CLI scaffold reserved for the future product UI wired to `backend/rest-api`). The new app is a self-contained tour of modern Angular capabilities — no backend dependency, all data is in-memory/mocked. It uses Angular 22 (latest stable) and Angular Material for UI components, with one lazy-loaded route per capability, plus unit tests (Jasmine/Karma) and e2e tests (Playwright).

## Goals

- Demonstrate the framework's main modern capabilities in isolated, readable examples.
- Each capability lives in its own folder with its own tests — a reader can open one folder and understand one feature without reading the rest of the app.
- Runs standalone (`npm start`) with zero external dependencies (no backend, no database, no Docker).

## Non-goals

- No SSR (kept as a plain SPA — SSR is reserved for `frontend/angular/` if/when it becomes the real product UI).
- No zoneless change detection (still developer-preview in v22; out of scope for a "main capabilities" tour).
- No integration with `backend/rest-api` — HTTP/data-fetching patterns are demoed against an in-memory fake API service with simulated network latency.
- No Gatling/load testing — not applicable to a frontend app.

## Repository structure

```
frontend/
├── angular/                              (existing, untouched scaffold)
└── angular-demo/
    ├── angular.json
    ├── package.json                      (Angular ^22.0.0, @angular/material ^22.0.0; "start" script uses --port 4201)
    ├── playwright.config.ts               (webServer runs `npm start`, baseURL http://localhost:4201)
    ├── e2e/
    │   ├── navigation.spec.ts            (smoke test: sidenav -> every route renders)
    │   ├── forms.spec.ts
    │   ├── data-fetching.spec.ts
    │   └── deferred-loading.spec.ts
    ├── README.md
    └── src/
        ├── main.ts
        ├── styles.scss                   (Angular Material theme setup)
        └── app/
            ├── app.ts                    (root shell: mat-sidenav + mat-toolbar + router-outlet)
            ├── app.routes.ts             (lazy-loaded child routes, one per feature)
            ├── app.config.ts             (provideRouter, provideHttpClient(withFetch(), withInterceptors(...)), provideAnimationsAsync)
            └── features/
                ├── signals/
                │   ├── signals-demo.ts             (signal/computed/effect walkthrough)
                │   └── signals-demo.spec.ts
                ├── component-communication/
                │   ├── component-communication-demo.ts   (container using input()/output()/model())
                │   ├── child-card.ts                     (child: signal input()/output()/model(), ng-content slots)
                │   └── *.spec.ts
                ├── forms/
                │   ├── forms-demo.ts               (ReactiveFormsModule, FormBuilder, validators, Material form fields)
                │   └── forms-demo.spec.ts
                ├── data-fetching/
                │   ├── data-fetching-demo.ts        (HttpClient + toSignal/toObservable)
                │   ├── fake-api.service.ts           (in-memory data, RxJS delay() to simulate latency)
                │   ├── logging.interceptor.ts        (functional HttpInterceptorFn)
                │   └── *.spec.ts                     (covers service + interceptor + component)
                ├── deferred-loading/
                │   ├── deferred-loading-demo.ts      (@defer with placeholder/loading/error, on viewport)
                │   └── deferred-loading-demo.spec.ts
                ├── routing/
                │   ├── routing-demo.ts               (explains/hosts the route-param + guard + resolver sub-route)
                │   ├── item-detail.ts                (route param + resolver-provided data)
                │   ├── has-selection.guard.ts         (functional CanActivateFn)
                │   └── *.spec.ts
                ├── pipes/
                │   ├── pipes-demo.ts
                │   ├── truncate.pipe.ts              (custom pure pipe)
                │   └── *.spec.ts
                ├── directives/
                │   ├── directives-demo.ts
                │   ├── highlight.directive.ts        (custom attribute directive)
                │   ├── repeat-if.directive.ts         (custom structural directive)
                │   └── *.spec.ts
                └── animations/
                    ├── animations-demo.ts             (@angular/animations trigger on a Material list)
                    └── animations-demo.spec.ts
```

## Feature tour (routes)

| Route | Capability demonstrated |
|---|---|
| `/signals` | `signal`, `computed`, `effect`; signal-based local state |
| `/component-communication` | signal-based `input()` / `output()` / `model()`; content projection (`ng-content`, multi-slot) |
| `/forms` | Reactive Forms, `FormBuilder`, validators, Material form fields |
| `/data-fetching` | `HttpClient` (fetch-based via `withFetch()`), functional interceptor, RxJS, `toSignal`/`toObservable`, against `FakeApiService` (in-memory, simulated latency) |
| `/deferred-loading` | `@defer` blocks — `@placeholder`, `@loading`, `@error`, `on viewport` trigger |
| `/routing` | route params, functional guard (`CanActivateFn`), functional resolver |
| `/pipes` | one custom pure pipe + a couple of built-in pipes for contrast |
| `/directives` | one custom attribute directive, one custom structural directive |
| `/animations` | `@angular/animations` enter/leave triggers on a Material list |

Shell (`app.ts`) is a `mat-sidenav` + `mat-toolbar` listing all 9 topics; `/` redirects to `/signals`.

## Testing strategy

- **Unit tests** — Jasmine/Karma (Angular CLI default, `ng test`, ChromeHeadless). Every feature folder carries `.spec.ts` files for its components, services, pipes, directives, guards, and interceptors (e.g. signal `computed()` values in `/signals`, validator behavior in `/forms`, `FakeApiService` + interceptor logic in `/data-fetching`, custom pipe/directive transform logic).
- **E2E tests** — Playwright, new `e2e/` folder at the app root, `playwright.config.ts` with a `webServer` block that starts `ng serve` automatically so `npx playwright test` is a single command:
  - `navigation.spec.ts` — smoke test: click every sidenav link, assert the corresponding route renders.
  - `forms.spec.ts` — fill the reactive form, assert validation error/success states.
  - `data-fetching.spec.ts` — trigger a fetch, assert the loading state then the populated list.
  - `deferred-loading.spec.ts` — scroll to the `@defer` block, assert it renders after entering the viewport.

## Commands (added to CLAUDE.md)

```bash
cd frontend/angular-demo

npm install
npm start              # dev server on :4201 (frontend/angular already owns :4200)
npm test                # Jasmine/Karma unit tests
npx playwright test     # e2e tests (auto-starts ng serve)
npm run build           # production build
```
