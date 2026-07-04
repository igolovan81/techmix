# Angular Capabilities Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `frontend/angular-demo/`, a self-contained Angular application that tours the framework's main modern capabilities (signals, component communication, forms, HTTP/RxJS, `@defer`, routing guards/resolvers, pipes, directives, animations), each behind its own lazy-loaded route, with unit tests (Jasmine/Karma) and e2e tests (Playwright).

**Architecture:** One Angular CLI application, standalone components only (no NgModules), Angular Material for UI, a `mat-sidenav` shell listing 9 topics, each topic lazy-loaded from `src/app/features/<topic>/`. No backend, no SSR — all data is in-memory or served from a static JSON asset via real `HttpClient` calls.

**Tech Stack:** Angular ^21.2 (latest stable that supports the active Node version), Angular Material ^21.2, Angular CLI, Jasmine + Karma (`ng test`), Playwright (`@playwright/test`), TypeScript, SCSS.

## Global Constraints

- Angular, Angular CLI, and Angular Material versions are pinned to `^21.2.0`. Angular 22.0.5 is technically newer but requires Node `^22.22.3 || ^24.15.0 || >=26.0.0`, which no locally installed Node version satisfies (active version is v24.4.0); 21.2.17 requires Node `^20.19.0 || ^22.12.0 || >=24.0.0`, which v24.4.0 satisfies. Re-check `npm view @angular/core versions` if the environment's Node version changes.
- Dev server runs on port **4201** (`frontend/angular` already owns 4200).
- No SSR, no NgModules — standalone components/directives/pipes only.
- No backend, no Docker — the app must run with only `npm install` + `npm start`.
- Any HTTP call in the demo must be a real `HttpClient` request (fetch-based, `provideHttpClient(withFetch())`) against a static asset under `public/`, never `of(...)` — this is what makes `/data-fetching` an honest demo of `HttpClient`.
- Elements exercised by Playwright e2e tests must carry a `data-testid` attribute — don't rely on CSS classes or text content for e2e selectors.
- Unit tests use Jasmine/Karma via `ng test` (ChromeHeadless); e2e tests use Playwright via `npx playwright test`, config at the app root, `webServer` auto-starts `npm start`.
- Every feature folder under `src/app/features/` is self-contained: its component(s) plus any service/pipe/directive/guard/resolver it owns, plus their `.spec.ts` files.
- This Angular 21 scaffold is **zoneless by default** — no `zone.js` dependency, no `zone.js/testing`. Never use `fakeAsync`/`tick` in tests; for observables with real delays (e.g. `FakeApiService`'s `delay(400)`), use an `async` test function and `await new Promise((resolve) => setTimeout(resolve, <delay + margin>))` instead.
- `@angular/animations` is deprecated in this Angular version and must not be installed; Angular Material does not require it. The animations feature (Task 16) uses the native `animate.enter` / `animate.leave` template bindings instead of the legacy `trigger`/`transition`/`style`/`animate` API.
- Zoneless tests must not mix `fixture.detectChanges()` with `fixture.whenStable()`, and must not mutate a plain (non-signal) component field and expect a manual `detectChanges()` to reliably pick it up — this throws a spurious `NG0100: ExpressionChangedAfterItHasBeenCheckedError` in this Angular version's zoneless fixtures. Instead: drive test host state through a `signal()` and exclusively use `await fixture.whenStable()` (never a bare synchronous `fixture.detectChanges()`) both for the initial render and after every state change.

---

## Task 1: Scaffold the Angular application with Material

**Files:**
- Create: `frontend/angular-demo/` (entire Angular CLI project)
- Modify: `frontend/angular-demo/package.json` — `start` script

**Interfaces:**
- Produces: a buildable, testable Angular 21 standalone app at `frontend/angular-demo/`, Angular Material installed and themed, dev server bound to port 4201. All later tasks build inside this project.

- [ ] **Step 1: Scaffold the app**

Run from `frontend/`. Pin to `@angular/cli@21.2.17` explicitly — the `@latest` dist-tag can resolve to a pre-release with stricter Node engine requirements than the active Node version supports.

```bash
cd frontend
npx -y -p @angular/cli@21.2.17 ng new angular-demo --routing --style=scss --ssr=false --skip-git --package-manager=npm --test-runner=karma
```

Angular 21's CLI defaults to Vitest; `--test-runner=karma` is required to get Jasmine/Karma as specified.

If this fails with a Node engine error, run `node -v` and compare against the version the CLI printed, then switch with `nvm use <version>` before retrying.

- [ ] **Step 2: Verify the baseline builds and tests pass**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

Expected: both commands exit 0 (the default `App` unit test passes, the production build succeeds).

- [ ] **Step 3: Add Angular Material**

```bash
npx ng add @angular/material@21.2.14 --theme=azure-blue --typography --animations=enabled --skip-confirmation
```

(Angular Material's latest published 21.x patch is 21.2.14, slightly behind `@angular/core`'s 21.2.17 — that's fine, they don't need matching patch versions.)

This adds the Material theme import to `src/styles.scss` automatically. In this Angular 21 release, `ng add @angular/material` does **not** wire an animations provider — Material components no longer need `@angular/animations`/`provideAnimationsAsync()` (that package is now deprecated in favor of the native `animate.enter`/`animate.leave` template bindings used later in Task 16). Do not install `@angular/animations`.

- [ ] **Step 4: Pin the dev server to port 4201**

Edit `frontend/angular-demo/package.json`, change the `start` script:

```json
"start": "ng serve --port 4201",
```

- [ ] **Step 5: Re-verify after Material install**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npm start
```

Expected: tests and build still pass; `npm start` serves on `http://localhost:4201` (Ctrl+C to stop after confirming).

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): scaffold angular-demo app with Angular Material"
```

---

## Task 2: App shell — sidenav navigation and routing skeleton

**Files:**
- Create: `frontend/angular-demo/src/app/nav-items.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`
- Modify: `frontend/angular-demo/src/app/app.ts`
- Modify: `frontend/angular-demo/src/app/app.html`
- Modify: `frontend/angular-demo/src/app/app.scss`
- Modify: `frontend/angular-demo/src/app/app.spec.ts`

**Interfaces:**
- Consumes: nothing from other feature tasks yet.
- Produces: `NAV_ITEMS: NavItem[]` (exported from `nav-items.ts`, shape `{ path: string; label: string }`) — every later feature task appends one entry to this array and one route to `app.routes.ts`. Root path `''` redirects to `signals`.

- [ ] **Step 1: Create the nav items list**

`frontend/angular-demo/src/app/nav-items.ts`:

```ts
export interface NavItem {
  path: string;
  label: string;
}

export const NAV_ITEMS: NavItem[] = [
  { path: 'signals', label: 'Signals' },
  { path: 'component-communication', label: 'Component Communication' },
  { path: 'forms', label: 'Forms' },
  { path: 'data-fetching', label: 'Data Fetching' },
  { path: 'deferred-loading', label: 'Deferred Loading' },
  { path: 'routing', label: 'Routing' },
  { path: 'pipes', label: 'Pipes' },
  { path: 'directives', label: 'Directives' },
  { path: 'animations', label: 'Animations' },
];
```

- [ ] **Step 2: Write the failing shell test**

Replace `frontend/angular-demo/src/app/app.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { NAV_ITEMS } from './nav-items';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('creates the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders one sidenav link per NAV_ITEMS entry', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const links = fixture.nativeElement.querySelectorAll('a[mat-list-item]');
    expect(links.length).toBe(NAV_ITEMS.length);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL — the default template has no `a[mat-list-item]` elements (0 !== 9).

- [ ] **Step 4: Implement the shell**

`frontend/angular-demo/src/app/app.routes.ts`:

```ts
import { Routes } from '@angular/router';

export const routes: Routes = [{ path: '', pathMatch: 'full', redirectTo: 'signals' }];
```

`frontend/angular-demo/src/app/app.ts`:

```ts
import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { NAV_ITEMS } from './nav-items';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet, MatToolbarModule, MatSidenavModule, MatListModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = 'angular-demo';
  protected readonly navItems = NAV_ITEMS;
}
```

`frontend/angular-demo/src/app/app.html`:

```html
<mat-toolbar color="primary">
  <span>{{ title }} — Angular Capabilities Tour</span>
</mat-toolbar>

<mat-sidenav-container class="shell">
  <mat-sidenav mode="side" opened class="shell-nav">
    <mat-nav-list>
      @for (item of navItems; track item.path) {
        <a mat-list-item [routerLink]="['/', item.path]">{{ item.label }}</a>
      }
    </mat-nav-list>
  </mat-sidenav>
  <mat-sidenav-content class="shell-content">
    <router-outlet />
  </mat-sidenav-content>
</mat-sidenav-container>
```

`frontend/angular-demo/src/app/app.scss`:

```css
.shell {
  height: calc(100vh - 64px);
}

.shell-nav {
  width: 260px;
}

.shell-content {
  padding: 24px;
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS (2 specs).

- [ ] **Step 6: Verify the build still succeeds**

```bash
npm run build
```

Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add angular-demo sidenav shell and routing skeleton"
```

---

## Task 3: Feature — Signals (`signal`, `computed`, `effect`)

**Files:**
- Create: `frontend/angular-demo/src/app/features/signals/signals-demo.ts`
- Create: `frontend/angular-demo/src/app/features/signals/signals-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `SignalsDemo` component, routed at `/signals`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/signals/signals-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { SignalsDemo } from './signals-demo';

describe('SignalsDemo', () => {
  it('computes doubled from count and tracks history via effect', () => {
    const fixture = TestBed.createComponent(SignalsDemo);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.count()).toBe(0);
    expect(component.doubled()).toBe(0);
    expect(component.history()).toEqual([0]);

    component.increment();
    fixture.detectChanges();
    expect(component.count()).toBe(1);
    expect(component.doubled()).toBe(2);
    expect(component.history()).toEqual([0, 1]);

    component.reset();
    fixture.detectChanges();
    expect(component.count()).toBe(0);
    expect(component.history()).toEqual([0, 1, 0]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './signals-demo'".

- [ ] **Step 3: Implement the component**

`frontend/angular-demo/src/app/features/signals/signals-demo.ts`:

```ts
import { Component, computed, effect, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-signals-demo',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  template: `
    <mat-card>
      <mat-card-title>Signals</mat-card-title>
      <mat-card-content>
        <p data-testid="count">Count: {{ count() }}</p>
        <p data-testid="doubled">Doubled (computed): {{ doubled() }}</p>
        <p data-testid="history-length">Effect history entries: {{ history().length }}</p>
        <button mat-raised-button color="primary" (click)="increment()" data-testid="increment">
          Increment
        </button>
        <button mat-stroked-button (click)="reset()" data-testid="reset">Reset</button>
      </mat-card-content>
    </mat-card>
  `,
})
export class SignalsDemo {
  readonly count = signal(0);
  readonly doubled = computed(() => this.count() * 2);
  readonly history = signal<number[]>([]);

  constructor() {
    effect(() => {
      const value = this.count();
      this.history.update((entries) => [...entries, value]);
    });
  }

  increment(): void {
    this.count.update((value) => value + 1);
  }

  reset(): void {
    this.count.set(0);
  }
}
```

- [ ] **Step 4: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, add before the redirect entry (order doesn't matter for matching since path segments differ, but keep the redirect readable at the top):

```ts
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'signals' },
  {
    path: 'signals',
    loadComponent: () => import('./features/signals/signals-demo').then((m) => m.SignalsDemo),
  },
];
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
git commit -m "feat(frontend): add signals capability demo"
```

---

## Task 4: Feature — Component communication (signal `input()`/`output()`/`model()`, content projection)

**Files:**
- Create: `frontend/angular-demo/src/app/features/component-communication/child-card.ts`
- Create: `frontend/angular-demo/src/app/features/component-communication/child-card.spec.ts`
- Create: `frontend/angular-demo/src/app/features/component-communication/component-communication-demo.ts`
- Create: `frontend/angular-demo/src/app/features/component-communication/component-communication-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `ChildCard` (selector `app-child-card`, `title = input.required<string>()`, `liked = model(false)`, `dismissed = output<void>()`, projection slots `[card-body]` / `[card-footer]`) and `ComponentCommunicationDemo`, routed at `/component-communication`.

- [ ] **Step 1: Write the failing test for the child**

`frontend/angular-demo/src/app/features/component-communication/child-card.spec.ts`:

```ts
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChildCard } from './child-card';

@Component({
  standalone: true,
  imports: [ChildCard],
  template: `<app-child-card title="Test" [(liked)]="liked" (dismissed)="dismissedCount = dismissedCount + 1" />`,
})
class HostComponent {
  liked = false;
  dismissedCount = 0;
}

describe('ChildCard', () => {
  it('toggles the model() signal and emits dismissed', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const card = fixture.debugElement.children[0].componentInstance as ChildCard;

    card.toggleLiked();
    fixture.detectChanges();
    expect(fixture.componentInstance.liked).toBe(true);

    card.dismissed.emit();
    expect(fixture.componentInstance.dismissedCount).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './child-card'".

- [ ] **Step 3: Implement the child component**

`frontend/angular-demo/src/app/features/component-communication/child-card.ts`:

```ts
import { Component, input, model, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-child-card',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  template: `
    <mat-card>
      <mat-card-title>{{ title() }}</mat-card-title>
      <mat-card-content>
        <ng-content select="[card-body]"></ng-content>
        <p data-testid="liked-state">Liked: {{ liked() }}</p>
      </mat-card-content>
      <mat-card-actions>
        <button mat-button (click)="toggleLiked()" data-testid="toggle-liked">Toggle Like</button>
        <button mat-button (click)="dismissed.emit()" data-testid="dismiss">Dismiss</button>
      </mat-card-actions>
      <ng-content select="[card-footer]"></ng-content>
    </mat-card>
  `,
})
export class ChildCard {
  readonly title = input.required<string>();
  readonly liked = model(false);
  readonly dismissed = output<void>();

  toggleLiked(): void {
    this.liked.update((value) => !value);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Write the failing test for the parent demo**

`frontend/angular-demo/src/app/features/component-communication/component-communication-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ComponentCommunicationDemo } from './component-communication-demo';

describe('ComponentCommunicationDemo', () => {
  it('reflects the child model() and counts dismiss events', () => {
    const fixture = TestBed.createComponent(ComponentCommunicationDemo);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.liked()).toBe(false);
    expect(component.dismissCount()).toBe(0);

    component.onDismissed();
    expect(component.dismissCount()).toBe(1);
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './component-communication-demo'".

- [ ] **Step 7: Implement the parent demo**

`frontend/angular-demo/src/app/features/component-communication/component-communication-demo.ts`:

```ts
import { Component, signal } from '@angular/core';
import { ChildCard } from './child-card';

@Component({
  selector: 'app-component-communication-demo',
  standalone: true,
  imports: [ChildCard],
  template: `
    <app-child-card title="Angular Signals" [(liked)]="liked" (dismissed)="onDismissed()">
      <p card-body>This card's "liked" state is a two-way bound model() signal.</p>
      <small card-footer>Projected via the ng-content footer slot.</small>
    </app-child-card>
    <p data-testid="parent-liked">Parent sees liked = {{ liked() }}</p>
    <p data-testid="dismiss-count">Dismiss count: {{ dismissCount() }}</p>
  `,
})
export class ComponentCommunicationDemo {
  readonly liked = signal(false);
  readonly dismissCount = signal(0);

  onDismissed(): void {
    this.dismissCount.update((value) => value + 1);
  }
}
```

- [ ] **Step 8: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'component-communication',
    loadComponent: () =>
      import('./features/component-communication/component-communication-demo').then(
        (m) => m.ComponentCommunicationDemo,
      ),
  },
```

- [ ] **Step 9: Run all tests to verify they pass**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add component communication capability demo"
```

---

## Task 5: Feature — Reactive Forms

**Files:**
- Create: `frontend/angular-demo/src/app/features/forms/forms-demo.ts`
- Create: `frontend/angular-demo/src/app/features/forms/forms-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `FormsDemo`, routed at `/forms`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/forms/forms-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { FormsDemo } from './forms-demo';

describe('FormsDemo', () => {
  it('is invalid until email and age pass validation, then submits', () => {
    const fixture = TestBed.createComponent(FormsDemo);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.signupForm.invalid).toBe(true);

    component.signupForm.controls.email.setValue('not-an-email');
    component.signupForm.controls.age.setValue(16);
    expect(component.signupForm.controls.email.invalid).toBe(true);
    expect(component.signupForm.controls.age.invalid).toBe(true);

    component.signupForm.controls.email.setValue('demo@example.com');
    component.signupForm.controls.age.setValue(21);
    expect(component.signupForm.valid).toBe(true);

    component.onSubmit();
    expect(component.submitted()).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './forms-demo'".

- [ ] **Step 3: Implement the component**

`frontend/angular-demo/src/app/features/forms/forms-demo.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-forms-demo',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <form [formGroup]="signupForm" (ngSubmit)="onSubmit()">
      <mat-form-field appearance="outline">
        <mat-label>Email</mat-label>
        <input matInput formControlName="email" data-testid="email-input" />
        @if (signupForm.controls.email.invalid && signupForm.controls.email.touched) {
          <mat-error data-testid="email-error">Enter a valid email address</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Age</mat-label>
        <input matInput type="number" formControlName="age" data-testid="age-input" />
        @if (signupForm.controls.age.invalid && signupForm.controls.age.touched) {
          <mat-error data-testid="age-error">Must be 18 or older</mat-error>
        }
      </mat-form-field>
      <button
        mat-raised-button
        color="primary"
        type="submit"
        [disabled]="signupForm.invalid"
        data-testid="submit-button"
      >
        Submit
      </button>
    </form>
    @if (submitted()) {
      <p data-testid="submit-success">Submitted: {{ signupForm.value.email }}</p>
    }
  `,
})
export class FormsDemo {
  private readonly fb = inject(FormBuilder);
  readonly submitted = signal(false);

  readonly signupForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    age: [18, [Validators.required, Validators.min(18)]],
  });

  onSubmit(): void {
    if (this.signupForm.valid) {
      this.submitted.set(true);
    }
  }
}
```

- [ ] **Step 4: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'forms',
    loadComponent: () => import('./features/forms/forms-demo').then((m) => m.FormsDemo),
  },
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
git commit -m "feat(frontend): add reactive forms capability demo"
```

---

## Task 6: Feature — Data fetching, part 1: `FakeApiService`

**Files:**
- Create: `frontend/angular-demo/public/data/items.json`
- Create: `frontend/angular-demo/src/app/features/data-fetching/fake-api.service.ts`
- Create: `frontend/angular-demo/src/app/features/data-fetching/fake-api.service.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.config.ts` — add `provideHttpClient(withFetch())`

**Interfaces:**
- Consumes: nothing.
- Produces: `FakeApiService.getItems(): Observable<DemoItem[]>` (real `HttpClient.get` against `data/items.json`, delayed 400ms), `DemoItem { id: number; name: string }` — consumed by Task 8's `DataFetchingDemo`.

- [ ] **Step 1: Create the static data asset**

`frontend/angular-demo/public/data/items.json`:

```json
[
  { "id": 1, "name": "Signals" },
  { "id": 2, "name": "Standalone Components" },
  { "id": 3, "name": "Deferred Loading" }
]
```

- [ ] **Step 2: Write the failing test**

`frontend/angular-demo/src/app/features/data-fetching/fake-api.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DemoItem, FakeApiService } from './fake-api.service';

describe('FakeApiService', () => {
  let service: FakeApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FakeApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches items from data/items.json via HttpClient', async () => {
    const expected: DemoItem[] = [{ id: 1, name: 'Signals' }];
    let received: DemoItem[] | undefined;

    service.getItems().subscribe((items) => (received = items));

    const req = httpMock.expectOne('data/items.json');
    expect(req.request.method).toBe('GET');
    req.flush(expected);

    await new Promise((resolve) => setTimeout(resolve, 450));

    expect(received).toEqual(expected);
  });
});
```

Note: `getItems()` pipes through `delay(400)`, so the emission is asynchronous. This project is zoneless (no `zone.js`), so `fakeAsync`/`tick` are unavailable — use a real `async` test with `setTimeout` instead.

- [ ] **Step 3: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './fake-api.service'".

- [ ] **Step 4: Implement the service**

`frontend/angular-demo/src/app/features/data-fetching/fake-api.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { delay } from 'rxjs/operators';

export interface DemoItem {
  id: number;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class FakeApiService {
  private readonly http = inject(HttpClient);

  getItems(): Observable<DemoItem[]> {
    return this.http.get<DemoItem[]>('data/items.json').pipe(delay(400));
  }
}
```

- [ ] **Step 5: Add the HttpClient provider**

In `frontend/angular-demo/src/app/app.config.ts`, add to the `providers` array (keep the existing `provideRouter(routes)` entry):

```ts
import { provideHttpClient, withFetch } from '@angular/common/http';
// ... existing imports

// inside ApplicationConfig.providers array:
provideHttpClient(withFetch()),
```

- [ ] **Step 6: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add FakeApiService backed by real HttpClient"
```

---

## Task 7: Feature — Data fetching, part 2: logging interceptor

**Files:**
- Create: `frontend/angular-demo/src/app/features/data-fetching/logging.interceptor.ts`
- Create: `frontend/angular-demo/src/app/features/data-fetching/logging.interceptor.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.config.ts` — add `withInterceptors([loggingInterceptor])`

**Interfaces:**
- Consumes: nothing directly (registered globally in `app.config.ts`).
- Produces: `loggingInterceptor: HttpInterceptorFn`, registered for every HTTP call the app makes (currently only `FakeApiService`).

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/data-fetching/logging.interceptor.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { loggingInterceptor } from './logging.interceptor';

describe('loggingInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([loggingInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('logs the completed request to the console', () => {
    const logSpy = spyOn(console, 'debug');

    http.get('data/items.json').subscribe();
    httpMock.expectOne('data/items.json').flush([]);

    expect(logSpy).toHaveBeenCalledWith(jasmine.stringMatching(/GET data\/items\.json completed/));
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './logging.interceptor'".

- [ ] **Step 3: Implement the interceptor**

`frontend/angular-demo/src/app/features/data-fetching/logging.interceptor.ts`:

```ts
import { HttpInterceptorFn } from '@angular/common/http';
import { tap } from 'rxjs/operators';

export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const start = Date.now();
  return next(req).pipe(
    tap({
      next: () => console.debug(`[HTTP] ${req.method} ${req.url} completed in ${Date.now() - start}ms`),
      error: (error) =>
        console.debug(`[HTTP] ${req.method} ${req.url} failed after ${Date.now() - start}ms`, error),
    }),
  );
};
```

- [ ] **Step 4: Register the interceptor**

In `frontend/angular-demo/src/app/app.config.ts`, update the import and the `provideHttpClient` call added in Task 6:

```ts
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { loggingInterceptor } from './features/data-fetching/logging.interceptor';

// inside providers array, replace the Task 6 line with:
provideHttpClient(withFetch(), withInterceptors([loggingInterceptor])),
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
git commit -m "feat(frontend): add HttpClient logging interceptor"
```

---

## Task 8: Feature — Data fetching, part 3: `DataFetchingDemo` component + route

**Files:**
- Create: `frontend/angular-demo/src/app/features/data-fetching/data-fetching-demo.ts`
- Create: `frontend/angular-demo/src/app/features/data-fetching/data-fetching-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `FakeApiService.getItems()` (Task 6), `DemoItem` (Task 6).
- Produces: `DataFetchingDemo`, routed at `/data-fetching`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/data-fetching/data-fetching-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DataFetchingDemo } from './data-fetching-demo';

describe('DataFetchingDemo', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows items once the HTTP call resolves', async () => {
    const fixture = TestBed.createComponent(DataFetchingDemo);
    fixture.detectChanges();

    expect(fixture.componentInstance.items()).toBeUndefined();

    httpMock.expectOne('data/items.json').flush([{ id: 1, name: 'Signals' }]);
    await new Promise((resolve) => setTimeout(resolve, 450));
    fixture.detectChanges();

    expect(fixture.componentInstance.items()).toEqual([{ id: 1, name: 'Signals' }]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './data-fetching-demo'".

- [ ] **Step 3: Implement the component**

`frontend/angular-demo/src/app/features/data-fetching/data-fetching-demo.ts`:

```ts
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FakeApiService } from './fake-api.service';

@Component({
  selector: 'app-data-fetching-demo',
  standalone: true,
  imports: [MatListModule, MatProgressSpinnerModule],
  template: `
    @if (items() === undefined) {
      <mat-spinner data-testid="loading-spinner" diameter="32"></mat-spinner>
    } @else {
      <mat-nav-list data-testid="items-list">
        @for (item of items(); track item.id) {
          <mat-list-item>{{ item.name }}</mat-list-item>
        }
      </mat-nav-list>
    }
  `,
})
export class DataFetchingDemo {
  private readonly api = inject(FakeApiService);
  readonly items = toSignal(this.api.getItems());
}
```

- [ ] **Step 4: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'data-fetching',
    loadComponent: () =>
      import('./features/data-fetching/data-fetching-demo').then((m) => m.DataFetchingDemo),
  },
```

- [ ] **Step 5: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 6: Manually verify the real HTTP round-trip**

```bash
npm start
```

Open `http://localhost:4201/data-fetching`, confirm the spinner appears briefly then the three items render. Stop the server (Ctrl+C).

- [ ] **Step 7: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add data fetching capability demo"
```

---

## Task 9: Feature — Deferred loading (`@defer`)

**Files:**
- Create: `frontend/angular-demo/src/app/features/deferred-loading/heavy-widget.ts`
- Create: `frontend/angular-demo/src/app/features/deferred-loading/deferred-loading-demo.ts`
- Create: `frontend/angular-demo/src/app/features/deferred-loading/deferred-loading-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `HeavyWidget`, `DeferredLoadingDemo`, routed at `/deferred-loading`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/deferred-loading/deferred-loading-demo.spec.ts`:

```ts
import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { DeferredLoadingDemo } from './deferred-loading-demo';

describe('DeferredLoadingDemo', () => {
  it('renders the placeholder, then the widget once the defer block completes', async () => {
    TestBed.configureTestingModule({
      imports: [DeferredLoadingDemo],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    await TestBed.compileComponents();
    const fixture = TestBed.createComponent(DeferredLoadingDemo);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="defer-placeholder"]')).toBeTruthy();

    const [deferBlockFixture] = await fixture.getDeferBlocks();
    await deferBlockFixture.render(DeferBlockState.Complete);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="heavy-widget"]')).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './deferred-loading-demo'".

- [ ] **Step 3: Implement the heavy widget and the demo**

`frontend/angular-demo/src/app/features/deferred-loading/heavy-widget.ts`:

```ts
import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-heavy-widget',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card data-testid="heavy-widget">
      <mat-card-content>Heavy widget loaded via &#64;defer.</mat-card-content>
    </mat-card>
  `,
})
export class HeavyWidget {}
```

`frontend/angular-demo/src/app/features/deferred-loading/deferred-loading-demo.ts`:

```ts
import { Component } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HeavyWidget } from './heavy-widget';

@Component({
  selector: 'app-deferred-loading-demo',
  standalone: true,
  imports: [MatProgressSpinnerModule, HeavyWidget],
  template: `
    <div style="height: 1200px;">Scroll down to trigger &#64;defer (on viewport).</div>
    @defer (on viewport) {
      <app-heavy-widget />
    } @placeholder (minimum 200ms) {
      <p data-testid="defer-placeholder">Placeholder: scroll into view to load.</p>
    } @loading (minimum 200ms) {
      <mat-spinner data-testid="defer-loading" diameter="24"></mat-spinner>
    } @error {
      <p data-testid="defer-error">Failed to load widget.</p>
    }
  `,
})
export class DeferredLoadingDemo {}
```

- [ ] **Step 4: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'deferred-loading',
    loadComponent: () =>
      import('./features/deferred-loading/deferred-loading-demo').then((m) => m.DeferredLoadingDemo),
  },
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
git commit -m "feat(frontend): add deferred loading capability demo"
```

---

## Task 10: Feature — Routing, part 1: `SelectionStore` and `hasSelectionGuard`

**Files:**
- Create: `frontend/angular-demo/src/app/features/routing/selection-store.ts`
- Create: `frontend/angular-demo/src/app/features/routing/has-selection.guard.ts`
- Create: `frontend/angular-demo/src/app/features/routing/has-selection.guard.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `SelectionStore` (`select(id: number): void`, `hasSelection(): boolean`), `hasSelectionGuard: CanActivateFn` — consumed by Task 12's route config.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/routing/has-selection.guard.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { hasSelectionGuard } from './has-selection.guard';
import { SelectionStore } from './selection-store';

describe('hasSelectionGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  it('blocks activation until a selection has been made', () => {
    TestBed.configureTestingModule({});
    const store = TestBed.inject(SelectionStore);

    const before = TestBed.runInInjectionContext(() => hasSelectionGuard(route, state));
    expect(before).toBe(false);

    store.select(1);

    const after = TestBed.runInInjectionContext(() => hasSelectionGuard(route, state));
    expect(after).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './has-selection.guard'".

- [ ] **Step 3: Implement the store and the guard**

`frontend/angular-demo/src/app/features/routing/selection-store.ts`:

```ts
import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SelectionStore {
  private readonly selectedId = signal<number | null>(null);

  select(id: number): void {
    this.selectedId.set(id);
  }

  hasSelection(): boolean {
    return this.selectedId() !== null;
  }
}
```

`frontend/angular-demo/src/app/features/routing/has-selection.guard.ts`:

```ts
import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { SelectionStore } from './selection-store';

export const hasSelectionGuard: CanActivateFn = () => {
  const store = inject(SelectionStore);
  return store.hasSelection();
};
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
git commit -m "feat(frontend): add selection store and route guard for routing demo"
```

---

## Task 11: Feature — Routing, part 2: resolver and `ItemDetail`

**Files:**
- Create: `frontend/angular-demo/src/app/features/routing/demo-items.ts`
- Create: `frontend/angular-demo/src/app/features/routing/item-detail.resolver.ts`
- Create: `frontend/angular-demo/src/app/features/routing/item-detail.resolver.spec.ts`
- Create: `frontend/angular-demo/src/app/features/routing/item-detail.ts`
- Create: `frontend/angular-demo/src/app/features/routing/item-detail.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `DEMO_ITEMS: DemoRoutingItem[]`, `itemDetailResolver: ResolveFn<DemoRoutingItem | undefined>`, `ItemDetail` component (reads resolved data from `route.data['item']`) — all consumed by Task 12's route config.

- [ ] **Step 1: Write the failing resolver test**

`frontend/angular-demo/src/app/features/routing/item-detail.resolver.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, convertToParamMap } from '@angular/router';
import { itemDetailResolver } from './item-detail.resolver';
import { DEMO_ITEMS } from './demo-items';

describe('itemDetailResolver', () => {
  it('resolves the item matching the :id route param', () => {
    const snapshot = { paramMap: convertToParamMap({ id: '2' }) } as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      itemDetailResolver(snapshot, {} as RouterStateSnapshot),
    );

    expect(result).toEqual(DEMO_ITEMS[1]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './item-detail.resolver'".

- [ ] **Step 3: Implement the data and resolver**

`frontend/angular-demo/src/app/features/routing/demo-items.ts`:

```ts
export interface DemoRoutingItem {
  id: number;
  name: string;
  description: string;
}

export const DEMO_ITEMS: DemoRoutingItem[] = [
  { id: 1, name: 'Route Params', description: 'The :id segment is read from ActivatedRoute.' },
  { id: 2, name: 'Guards', description: 'CanActivateFn blocks direct navigation without a selection.' },
  { id: 3, name: 'Resolvers', description: 'ResolveFn preloads data before the route activates.' },
];
```

`frontend/angular-demo/src/app/features/routing/item-detail.resolver.ts`:

```ts
import { ResolveFn } from '@angular/router';
import { DEMO_ITEMS, DemoRoutingItem } from './demo-items';

export const itemDetailResolver: ResolveFn<DemoRoutingItem | undefined> = (route) => {
  const id = Number(route.paramMap.get('id'));
  return DEMO_ITEMS.find((item) => item.id === id);
};
```

- [ ] **Step 4: Run resolver test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Write the failing `ItemDetail` test**

`frontend/angular-demo/src/app/features/routing/item-detail.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ItemDetail } from './item-detail';
import { DEMO_ITEMS } from './demo-items';

describe('ItemDetail', () => {
  it('renders the item resolved into route.data', () => {
    TestBed.configureTestingModule({
      imports: [ItemDetail],
      providers: [
        { provide: ActivatedRoute, useValue: { data: of({ item: DEMO_ITEMS[0] }) } },
      ],
    });

    const fixture = TestBed.createComponent(ItemDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(DEMO_ITEMS[0].name);
    expect(fixture.nativeElement.textContent).toContain(DEMO_ITEMS[0].description);
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './item-detail'".

- [ ] **Step 7: Implement `ItemDetail`**

`frontend/angular-demo/src/app/features/routing/item-detail.ts`:

```ts
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { MatCardModule } from '@angular/material/card';
import { DemoRoutingItem } from './demo-items';

@Component({
  selector: 'app-item-detail',
  standalone: true,
  imports: [MatCardModule],
  template: `
    @if (item(); as value) {
      <mat-card data-testid="item-detail">
        <mat-card-title>{{ value.name }}</mat-card-title>
        <mat-card-content>{{ value.description }}</mat-card-content>
      </mat-card>
    }
  `,
})
export class ItemDetail {
  private readonly route = inject(ActivatedRoute);
  readonly item = toSignal(this.route.data.pipe(map((data) => data['item'] as DemoRoutingItem)));
}
```

- [ ] **Step 8: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add routing resolver and item detail component"
```

---

## Task 12: Feature — Routing, part 3: `RoutingDemo` host + nested route wiring

**Files:**
- Create: `frontend/angular-demo/src/app/features/routing/routing-demo.ts`
- Create: `frontend/angular-demo/src/app/features/routing/routing-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `SelectionStore` (Task 10), `hasSelectionGuard` (Task 10), `DEMO_ITEMS` (Task 11), `itemDetailResolver` (Task 11), `ItemDetail` (Task 11).
- Produces: `RoutingDemo`, routed at `/routing` with a nested `item/:id` child route.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/routing/routing-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { RoutingDemo } from './routing-demo';
import { SelectionStore } from './selection-store';

describe('RoutingDemo', () => {
  it('selects the item and navigates to its detail route', () => {
    const navigateSpy = jasmine.createSpy('navigate');

    TestBed.configureTestingModule({
      imports: [RoutingDemo],
      providers: [
        { provide: Router, useValue: { navigate: navigateSpy } },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });

    const fixture = TestBed.createComponent(RoutingDemo);
    fixture.detectChanges();

    const store = TestBed.inject(SelectionStore);
    fixture.componentInstance.select(1);

    expect(store.hasSelection()).toBe(true);
    expect(navigateSpy).toHaveBeenCalledWith(['item', 1], { relativeTo: jasmine.any(Object) });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './routing-demo'".

- [ ] **Step 3: Implement the component**

`frontend/angular-demo/src/app/features/routing/routing-demo.ts`:

```ts
import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { SelectionStore } from './selection-store';
import { DEMO_ITEMS } from './demo-items';

@Component({
  selector: 'app-routing-demo',
  standalone: true,
  imports: [MatListModule, RouterOutlet],
  template: `
    <mat-nav-list>
      @for (item of items; track item.id) {
        <a mat-list-item (click)="select(item.id)" [attr.data-testid]="'route-item-' + item.id">
          {{ item.name }}
        </a>
      }
    </mat-nav-list>
    <router-outlet />
  `,
})
export class RoutingDemo {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly selectionStore = inject(SelectionStore);
  readonly items = DEMO_ITEMS;

  select(id: number): void {
    this.selectionStore.select(id);
    this.router.navigate(['item', id], { relativeTo: this.route });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Wire the nested route**

In `frontend/angular-demo/src/app/app.routes.ts`, add the necessary imports and append the route:

```ts
import { hasSelectionGuard } from './features/routing/has-selection.guard';
import { itemDetailResolver } from './features/routing/item-detail.resolver';

// appended to the routes array:
  {
    path: 'routing',
    loadComponent: () => import('./features/routing/routing-demo').then((m) => m.RoutingDemo),
    children: [
      {
        path: 'item/:id',
        canActivate: [hasSelectionGuard],
        resolve: { item: itemDetailResolver },
        loadComponent: () => import('./features/routing/item-detail').then((m) => m.ItemDetail),
      },
    ],
  },
```

- [ ] **Step 6: Manually verify the guard and resolver end-to-end**

```bash
npm start
```

Open `http://localhost:4201/routing/item/1` directly (no prior selection) — confirm the guard redirects/blocks (no `item-detail` card renders). Then open `http://localhost:4201/routing`, click "Route Params", confirm the detail card renders. Stop the server (Ctrl+C).

- [ ] **Step 7: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): wire routing capability demo with guard and resolver"
```

---

## Task 13: Feature — Pipes (custom pipe + built-ins)

**Files:**
- Create: `frontend/angular-demo/src/app/features/pipes/truncate.pipe.ts`
- Create: `frontend/angular-demo/src/app/features/pipes/truncate.pipe.spec.ts`
- Create: `frontend/angular-demo/src/app/features/pipes/pipes-demo.ts`
- Create: `frontend/angular-demo/src/app/features/pipes/pipes-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `TruncatePipe` (name `truncate`), `PipesDemo`, routed at `/pipes`.

- [ ] **Step 1: Write the failing pipe test**

`frontend/angular-demo/src/app/features/pipes/truncate.pipe.spec.ts`:

```ts
import { TruncatePipe } from './truncate.pipe';

describe('TruncatePipe', () => {
  const pipe = new TruncatePipe();

  it('returns the value unchanged when shorter than maxLength', () => {
    expect(pipe.transform('short', 20)).toBe('short');
  });

  it('truncates and appends an ellipsis when longer than maxLength', () => {
    expect(pipe.transform('this text is definitely too long', 10)).toBe('this text…');
  });

  it('defaults maxLength to 20', () => {
    expect(pipe.transform('exactly twenty chars')).toBe('exactly twenty chars');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './truncate.pipe'".

- [ ] **Step 3: Implement the pipe**

`frontend/angular-demo/src/app/features/pipes/truncate.pipe.ts`:

```ts
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'truncate', standalone: true, pure: true })
export class TruncatePipe implements PipeTransform {
  transform(value: string, maxLength = 20): string {
    if (value.length <= maxLength) {
      return value;
    }
    return `${value.slice(0, maxLength).trimEnd()}…`;
  }
}
```

- [ ] **Step 4: Run pipe test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Write the failing demo test**

`frontend/angular-demo/src/app/features/pipes/pipes-demo.spec.ts`:

```ts
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
```

- [ ] **Step 6: Run test to verify it fails**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './pipes-demo'".

- [ ] **Step 7: Implement the demo component**

`frontend/angular-demo/src/app/features/pipes/pipes-demo.ts`:

```ts
import { Component, signal } from '@angular/core';
import { DatePipe, UpperCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { TruncatePipe } from './truncate.pipe';

@Component({
  selector: 'app-pipes-demo',
  standalone: true,
  imports: [MatCardModule, DatePipe, UpperCasePipe, TruncatePipe],
  template: `
    <mat-card>
      <p data-testid="truncated">{{ longText() | truncate: 24 }}</p>
      <p data-testid="uppercased">{{ shortText() | uppercase }}</p>
      <p data-testid="dated">{{ now | date: 'yyyy-MM-dd' }}</p>
    </mat-card>
  `,
})
export class PipesDemo {
  readonly longText = signal('Angular pipes transform displayed values declaratively.');
  readonly shortText = signal('angular');
  readonly now = new Date(2026, 0, 1);
}
```

- [ ] **Step 8: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'pipes',
    loadComponent: () => import('./features/pipes/pipes-demo').then((m) => m.PipesDemo),
  },
```

- [ ] **Step 9: Run all tests to verify they pass**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add pipes capability demo"
```

---

## Task 14: Feature — Directives, part 1: `HighlightDirective`

**Files:**
- Create: `frontend/angular-demo/src/app/features/directives/highlight.directive.ts`
- Create: `frontend/angular-demo/src/app/features/directives/highlight.directive.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `HighlightDirective` (selector `[appHighlight]`, input `appHighlight: string`) — consumed by Task 15's `DirectivesDemo`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/directives/highlight.directive.spec.ts`:

```ts
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HighlightDirective } from './highlight.directive';

@Component({
  standalone: true,
  imports: [HighlightDirective],
  template: `<p appHighlight="#c8e6c9">Hover me</p>`,
})
class HostComponent {}

describe('HighlightDirective', () => {
  it('sets and clears the background color on mouse enter/leave', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement.querySelector('p');

    el.dispatchEvent(new Event('mouseenter'));
    expect(el.style.backgroundColor).toBe('rgb(200, 230, 201)');

    el.dispatchEvent(new Event('mouseleave'));
    expect(el.style.backgroundColor).toBe('');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './highlight.directive'".

- [ ] **Step 3: Implement the directive**

`frontend/angular-demo/src/app/features/directives/highlight.directive.ts`:

```ts
import { Directive, ElementRef, HostListener, inject, input } from '@angular/core';

@Directive({ selector: '[appHighlight]', standalone: true })
export class HighlightDirective {
  private readonly el = inject(ElementRef<HTMLElement>);
  readonly appHighlight = input('#fff59d');

  @HostListener('mouseenter')
  onMouseEnter(): void {
    this.el.nativeElement.style.backgroundColor = this.appHighlight();
  }

  @HostListener('mouseleave')
  onMouseLeave(): void {
    this.el.nativeElement.style.backgroundColor = '';
  }
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
git commit -m "feat(frontend): add custom attribute directive for directives demo"
```

---

## Task 15: Feature — Directives, part 2: `RepeatDirective` + `DirectivesDemo` + route

**Files:**
- Create: `frontend/angular-demo/src/app/features/directives/repeat.directive.ts`
- Create: `frontend/angular-demo/src/app/features/directives/repeat.directive.spec.ts`
- Create: `frontend/angular-demo/src/app/features/directives/directives-demo.ts`
- Create: `frontend/angular-demo/src/app/features/directives/directives-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `HighlightDirective` (Task 14).
- Produces: `RepeatDirective` (selector `[appRepeat]`), `DirectivesDemo`, routed at `/directives`.

- [ ] **Step 1: Write the failing structural directive test**

`frontend/angular-demo/src/app/features/directives/repeat.directive.spec.ts`:

```ts
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RepeatDirective } from './repeat.directive';

@Component({
  imports: [RepeatDirective],
  template: `<li *appRepeat="count()">Item</li>`,
})
class HostComponent {
  readonly count = signal(3);
}

describe('RepeatDirective', () => {
  it('renders the template once per repeat count and re-renders on change', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(3);

    fixture.componentInstance.count.set(5);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(5);
  });
});
```

Note: drive the host's repeat count through a `signal()` and use `await fixture.whenStable()` exclusively (never mix in a bare `fixture.detectChanges()`) — see the zoneless-testing constraint above.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './repeat.directive'".

- [ ] **Step 3: Implement the structural directive**

`frontend/angular-demo/src/app/features/directives/repeat.directive.ts`:

```ts
import { Directive, Input, OnChanges, TemplateRef, ViewContainerRef, inject } from '@angular/core';

@Directive({ selector: '[appRepeat]' })
export class RepeatDirective implements OnChanges {
  private readonly templateRef = inject(TemplateRef<{ $implicit: number }>);
  private readonly viewContainerRef = inject(ViewContainerRef);

  @Input({ required: true }) appRepeat = 0;

  ngOnChanges(): void {
    this.viewContainerRef.clear();
    for (let i = 0; i < this.appRepeat; i++) {
      this.viewContainerRef.createEmbeddedView(this.templateRef, { $implicit: i });
    }
  }
}
```

Note: create the embedded views from `ngOnChanges`, not from the `@Input` setter directly — setting view-container state inside an input setter trips a spurious `ExpressionChangedAfterItHasBeenCheckedError` in this Angular version.

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 5: Write the failing demo test**

`frontend/angular-demo/src/app/features/directives/directives-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { DirectivesDemo } from './directives-demo';

describe('DirectivesDemo', () => {
  it('renders repeatCount() list items', () => {
    const fixture = TestBed.createComponent(DirectivesDemo);
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('[data-testid="repeat-list"] li');
    expect(items.length).toBe(fixture.componentInstance.repeatCount());
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './directives-demo'".

- [ ] **Step 7: Implement the demo component**

`frontend/angular-demo/src/app/features/directives/directives-demo.ts`:

```ts
import { Component, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { HighlightDirective } from './highlight.directive';
import { RepeatDirective } from './repeat.directive';

@Component({
  selector: 'app-directives-demo',
  standalone: true,
  imports: [MatCardModule, HighlightDirective, RepeatDirective],
  template: `
    <mat-card>
      <p appHighlight="#c8e6c9" data-testid="highlight-target">
        Hover to highlight (custom attribute directive).
      </p>
      <ul data-testid="repeat-list">
        <li *appRepeat="repeatCount()">Item repeated by *appRepeat</li>
      </ul>
    </mat-card>
  `,
})
export class DirectivesDemo {
  readonly repeatCount = signal(3);
}
```

- [ ] **Step 8: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'directives',
    loadComponent: () => import('./features/directives/directives-demo').then((m) => m.DirectivesDemo),
  },
```

- [ ] **Step 9: Run all tests to verify they pass**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add directives capability demo"
```

---

## Task 16: Feature — Animations

**Files:**
- Create: `frontend/angular-demo/src/app/features/animations/animations-demo.ts`
- Create: `frontend/angular-demo/src/app/features/animations/animations-demo.spec.ts`
- Modify: `frontend/angular-demo/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `AnimationsDemo`, routed at `/animations`.

- [ ] **Step 1: Write the failing test**

`frontend/angular-demo/src/app/features/animations/animations-demo.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { AnimationsDemo } from './animations-demo';

describe('AnimationsDemo', () => {
  it('adds and removes items from the animated list', async () => {
    const fixture = TestBed.createComponent(AnimationsDemo);
    await fixture.whenStable();

    fixture.componentInstance.add();
    fixture.componentInstance.add();
    await fixture.whenStable();
    expect(fixture.componentInstance.items().length).toBe(2);

    fixture.componentInstance.removeLast();
    await fixture.whenStable();
    expect(fixture.componentInstance.items().length).toBe(1);
  });
});
```

Note: asserts against the `items()` signal rather than DOM node count — `animate.leave` intentionally delays DOM removal until its exit animation finishes, so counting `<mat-list-item>` elements immediately after `removeLast()` would be flaky.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: FAIL with "Cannot find module './animations-demo'".

- [ ] **Step 3: Implement the component**

`frontend/angular-demo/src/app/features/animations/animations-demo.ts`:

```ts
import { Component, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-animations-demo',
  imports: [MatButtonModule, MatListModule],
  template: `
    <button mat-raised-button color="primary" (click)="add()" data-testid="add-button">Add item</button>
    <button mat-stroked-button (click)="removeLast()" data-testid="remove-button">Remove last</button>
    <mat-nav-list data-testid="animated-list">
      @for (item of items(); track item) {
        <mat-list-item animate.enter="fade-in" animate.leave="fade-out">{{ item }}</mat-list-item>
      }
    </mat-nav-list>
  `,
  styles: `
    .fade-in {
      animation: fade-slide-in 200ms ease-out;
    }
    .fade-out {
      animation: fade-slide-out 150ms ease-in;
    }
    @keyframes fade-slide-in {
      from {
        opacity: 0;
        transform: translateX(-16px);
      }
    }
    @keyframes fade-slide-out {
      to {
        opacity: 0;
        transform: translateX(16px);
      }
    }
  `,
})
export class AnimationsDemo {
  private counter = 0;
  readonly items = signal<string[]>([]);

  add(): void {
    this.counter += 1;
    this.items.update((current) => [...current, `Item ${this.counter}`]);
  }

  removeLast(): void {
    this.items.update((current) => current.slice(0, -1));
  }
}
```

Uses the native `animate.enter` / `animate.leave` template bindings (CSS-animation-driven, no `@angular/animations` package or providers needed) instead of the deprecated `trigger`/`transition`/`style`/`animate` API.

- [ ] **Step 4: Wire the route**

In `frontend/angular-demo/src/app/app.routes.ts`, append:

```ts
  {
    path: 'animations',
    loadComponent: () => import('./features/animations/animations-demo').then((m) => m.AnimationsDemo),
  },
```

- [ ] **Step 5: Run all tests to verify they pass**

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: PASS — all 9 features are now routed and app.routes.ts has one entry per NAV_ITEMS path.

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "feat(frontend): add animations capability demo"
```

---

## Task 17: E2E setup + navigation smoke test

**Files:**
- Create: `frontend/angular-demo/playwright.config.ts`
- Create: `frontend/angular-demo/e2e/navigation.spec.ts`
- Modify: `frontend/angular-demo/package.json` — devDependency `@playwright/test`, `.gitignore` additions

**Interfaces:**
- Consumes: `NAV_ITEMS` (Task 2) — the smoke test's route list is copied inline to keep `e2e/` independent of `src/` compilation (Playwright and the Angular app compile separately).
- Produces: a working `npx playwright test` command any later e2e task can add spec files alongside.

- [ ] **Step 1: Install Playwright**

```bash
cd frontend/angular-demo
npm install -D @playwright/test@1.61.1
npx playwright install --with-deps chromium
```

Pin to the latest stable (`1.61.1` at the time of this plan) — the `@latest` dist-tag can resolve to an alpha/next build; check `npm view @playwright/test dist-tags` if versions drift.

- [ ] **Step 2: Create the Playwright config**

`frontend/angular-demo/playwright.config.ts`:

```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: 0,
  timeout: 60_000,
  expect: {
    timeout: 15_000,
  },
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4201',
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
  use: {
    baseURL: 'http://localhost:4201',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
```

The generous `expect.timeout` (15s) accommodates `ng serve`'s on-demand compilation of each lazy-loaded route chunk on first visit — clicking through all 9 routes in one test can be slow on a cold dev-server start; the default 5s expect timeout is not enough.

- [ ] **Step 3: Write the navigation smoke test**

`frontend/angular-demo/e2e/navigation.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

const NAV_ITEMS = [
  { path: 'signals', label: 'Signals' },
  { path: 'component-communication', label: 'Component Communication' },
  { path: 'forms', label: 'Forms' },
  { path: 'data-fetching', label: 'Data Fetching' },
  { path: 'deferred-loading', label: 'Deferred Loading' },
  { path: 'routing', label: 'Routing' },
  { path: 'pipes', label: 'Pipes' },
  { path: 'directives', label: 'Directives' },
  { path: 'animations', label: 'Animations' },
];

test('sidenav links navigate to every feature route', async ({ page }) => {
  await page.goto('/');

  for (const item of NAV_ITEMS) {
    await page.getByRole('link', { name: item.label }).click();
    await expect(page).toHaveURL(new RegExp(`/${item.path}$`));
  }
});
```

- [ ] **Step 4: Add the `.gitignore` entry for Playwright artifacts**

Append to `frontend/angular-demo/.gitignore`:

```
/test-results/
/playwright-report/
/playwright/.cache/
```

- [ ] **Step 5: Run the e2e test to verify it passes**

```bash
cd frontend/angular-demo
npx playwright test
```

Expected: 1 passed (Playwright starts `npm start` automatically per `webServer.command`).

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "test(frontend): add Playwright e2e setup and navigation smoke test"
```

---

## Task 18: E2E — forms interaction test

**Files:**
- Create: `frontend/angular-demo/e2e/forms.spec.ts`

**Interfaces:**
- Consumes: `data-testid` attributes from `FormsDemo` (Task 5): `email-input`, `age-input`, `age-error`, `submit-button`, `submit-success`.

- [ ] **Step 1: Write the test**

`frontend/angular-demo/e2e/forms.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

test('reactive form validates and submits', async ({ page }) => {
  await page.goto('/forms');
  const submit = page.getByTestId('submit-button');
  await expect(submit).toBeDisabled();

  await page.getByTestId('email-input').fill('not-an-email');
  await page.getByTestId('age-input').fill('16');
  await page.getByTestId('age-input').blur();
  await expect(page.getByTestId('age-error')).toBeVisible();

  await page.getByTestId('email-input').fill('demo@example.com');
  await page.getByTestId('age-input').fill('21');
  await expect(submit).toBeEnabled();

  await submit.click();
  await expect(page.getByTestId('submit-success')).toContainText('demo@example.com');
});
```

- [ ] **Step 2: Run the test to verify it passes**

```bash
cd frontend/angular-demo
npx playwright test forms.spec.ts
```

Expected: 1 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "test(frontend): add e2e coverage for the forms demo"
```

---

## Task 19: E2E — data fetching interaction test

**Files:**
- Create: `frontend/angular-demo/e2e/data-fetching.spec.ts`

**Interfaces:**
- Consumes: `data-testid` attributes from `DataFetchingDemo` (Task 8): `loading-spinner`, `items-list`.

- [ ] **Step 1: Write the test**

`frontend/angular-demo/e2e/data-fetching.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

test('fetches and renders items from the fake API', async ({ page }) => {
  await page.goto('/data-fetching');

  await expect(page.getByTestId('loading-spinner')).toBeVisible();
  await expect(page.getByTestId('items-list')).toBeVisible();
  await expect(page.getByTestId('items-list')).toContainText('Signals');
});
```

- [ ] **Step 2: Run the test to verify it passes**

```bash
cd frontend/angular-demo
npx playwright test data-fetching.spec.ts
```

Expected: 1 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "test(frontend): add e2e coverage for the data fetching demo"
```

---

## Task 20: E2E — deferred loading interaction test

**Files:**
- Create: `frontend/angular-demo/e2e/deferred-loading.spec.ts`

**Interfaces:**
- Consumes: `data-testid` attributes from `DeferredLoadingDemo`/`HeavyWidget` (Task 9): `defer-placeholder`, `heavy-widget`.

- [ ] **Step 1: Write the test**

`frontend/angular-demo/e2e/deferred-loading.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

test('defer block loads the heavy widget once scrolled into view', async ({ page }) => {
  await page.goto('/deferred-loading');

  await expect(page.getByTestId('defer-placeholder')).toBeVisible();

  await page.getByTestId('defer-placeholder').scrollIntoViewIfNeeded();
  await page.locator('mat-sidenav-content').evaluate((el) => el.scrollTo(0, el.scrollHeight));

  await expect(page.getByTestId('heavy-widget')).toBeVisible();
});
```

Note: the app shell's scrollable region is `mat-sidenav-content` (Material's own internal overflow container), not the window — `page.mouse.wheel(...)` scrolls the window and never triggers the `@defer (on viewport)` intersection. Scroll the actual container directly instead.

- [ ] **Step 2: Run the test to verify it passes**

```bash
cd frontend/angular-demo
npx playwright test deferred-loading.spec.ts
```

Expected: 1 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo
git commit -m "test(frontend): add e2e coverage for the deferred loading demo"
```

---

## Task 21: README, CLAUDE.md, and final full verification

**Files:**
- Create: `frontend/angular-demo/README.md`
- Modify: `/Users/admin/IdeaProjects/private/techmix-copy/CLAUDE.md`

**Interfaces:**
- Consumes: the complete app from Tasks 1–20.
- Produces: documentation so a future reader/agent can run and extend the demo without re-deriving its structure.

- [ ] **Step 1: Write the README**

`frontend/angular-demo/README.md`:

```markdown
# Angular Capabilities Demo

A self-contained Angular 21 application that tours the framework's main modern
capabilities. No backend, no Docker — everything runs from `npm install` +
`npm start`.

## Commands

\`\`\`bash
npm install
npm start              # dev server on http://localhost:4201
npm test                # Jasmine/Karma unit tests
npx playwright test     # Playwright e2e tests (auto-starts the dev server)
npm run build           # production build
\`\`\`

## Feature tour

| Route | Capability |
|---|---|
| `/signals` | `signal`, `computed`, `effect` |
| `/component-communication` | signal `input()` / `output()` / `model()`, content projection |
| `/forms` | Reactive Forms, validators, Material form fields |
| `/data-fetching` | `HttpClient` (fetch-based), functional interceptor, RxJS, `toSignal` |
| `/deferred-loading` | `@defer` — placeholder/loading/error, `on viewport` |
| `/routing` | route params, functional guard, functional resolver |
| `/pipes` | custom pure pipe + built-in pipes |
| `/directives` | custom attribute directive + custom structural directive |
| `/animations` | `@angular/animations` enter/leave transitions |

Each feature lives in its own folder under `src/app/features/<topic>/`,
self-contained with its own tests.
```

- [ ] **Step 2: Update the root `CLAUDE.md`**

Add a new section to `/Users/admin/IdeaProjects/private/techmix-copy/CLAUDE.md`, in the "Commands" section, after the "Frontend (Angular 20 with SSR)" block:

```markdown
### Frontend Angular capabilities demo (self-contained, no backend)

\`\`\`bash
cd frontend/angular-demo

npm install
npm start                            # dev server on :4201
npm test                             # Jasmine/Karma unit tests
npx playwright test                  # e2e tests (auto-starts the dev server)
npm run build                        # production build
\`\`\`
```

And add a row to the "Repository layout" table:

```markdown
| `frontend/angular-demo/` | Self-contained Angular 21 app touring the framework's capabilities (signals, forms, routing guards/resolvers, `@defer`, etc.) — no backend |
```

- [ ] **Step 3: Run the full verification suite**

```bash
cd frontend/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npx playwright test
```

Expected: all unit tests pass, the production build succeeds, all e2e tests pass (navigation + forms + data-fetching + deferred-loading).

- [ ] **Step 4: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add frontend/angular-demo CLAUDE.md
git commit -m "docs(frontend): document angular-demo app in README and CLAUDE.md"
```
