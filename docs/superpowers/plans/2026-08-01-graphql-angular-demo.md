# GraphQL Angular Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `communication-protocols/graphql/angular-demo`, a standalone Angular 21 app that exercises all five GraphQL patterns in `graphql/spring-demo` (query + nested fetch, DataLoader batching, mutation, subscription, pagination & filtering) with role-based and row-level authorization, without modifying `graphql/spring-demo`.

**Architecture:** Standalone components + signals, Angular Material for UI, Apollo Angular (`apollo-angular` on `@apollo/client` v4) for GraphQL over HTTP and a `graphql-ws` WebSocket link for the `reviewAdded` subscription. A dev-server proxy (`proxy.conf.json`) makes the app same-origin with the Spring app, which sidesteps CORS and lets the browser carry the session cookie from the `me` login query onto the WebSocket handshake, authenticating the subscription. Each feature area has a thin Apollo-backed service (unit-tested with a mocked `Apollo`) plus dumb components that consume it (unit-tested with a mocked service).

**Tech Stack:** Angular 21, Angular Material + CDK, apollo-angular 14.x / `@apollo/client` ^4.0.1, `graphql-ws`, RxJS, Karma/Jasmine.

## Global Constraints

- Do not modify any file under `communication-protocols/graphql/spring-demo/`.
- App port is **4202** (4200 = `frontend/angular`, 4201 = `frontend/angular-demo`).
- No GraphQL codegen — `core/graphql/graphql.models.ts` types are hand-written.
- No e2e tests — Karma/Jasmine unit tests only.
- Components use signals (`signal`, `computed`, `input()`, `output()`) and the `@if`/`@for` control-flow syntax, matching `frontend/angular-demo`'s conventions. No `standalone: true` flag (implicit default), no NgModules.
- File names carry no `.component`/`.service` suffix (e.g. `product-list.ts`, class `ProductList`), matching `frontend/angular-demo`.
- Reactive forms use `inject(FormBuilder).nonNullable.group({...})`, matching `frontend/angular-demo/src/app/features/forms/forms-demo.ts`.
- Interactive elements used in tests carry a `data-testid` attribute.

---

### Task 1: Scaffold the app, Angular Material, Apollo dependencies, dev proxy

**Files:**
- Create: `communication-protocols/graphql/angular-demo/` (full Angular CLI scaffold)
- Modify: `communication-protocols/graphql/angular-demo/package.json` (`start` script)
- Create: `communication-protocols/graphql/angular-demo/proxy.conf.json`

**Interfaces:**
- Produces: a buildable, testable Angular 21 app shell with Angular Material and `apollo-angular`/`@apollo/client`/`graphql`/`graphql-ws` installed, that later tasks add files into.

- [ ] **Step 1: Generate the Angular app**

```bash
cd communication-protocols/graphql
ng new angular-demo --style=scss --routing=false --ssr=false --skip-git
cd angular-demo
```

If prompted interactively (exact prompts vary by CLI version — e.g. SSR, zoneless change detection, AI tooling integration), decline all of them: this app uses zone-based change detection and no SSR, matching `frontend/angular-demo`.

- [ ] **Step 2: Set the dev port and proxy config**

Edit `package.json`'s `scripts.start` to:

```json
"start": "ng serve --port 4202 --proxy-config proxy.conf.json",
```

Create `proxy.conf.json`:

```json
{
  "/graphql": {
    "target": "http://localhost:8092",
    "secure": false,
    "ws": true,
    "changeOrigin": true
  }
}
```

- [ ] **Step 3: Add Angular Material**

```bash
ng add @angular/material --theme=azure-blue --typography=true --animations=enabled --skip-confirmation
```

If any of these flags aren't recognized by the installed schematic version, run `ng add @angular/material` interactively instead and choose: a prebuilt theme (any), yes to global typography styles, yes to browser animations.

- [ ] **Step 4: Add GraphQL client dependencies**

```bash
npm install apollo-angular @apollo/client graphql graphql-ws
```

- [ ] **Step 5: Verify the scaffold builds and tests**

```bash
npm run build
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: both succeed (the generated default `App` component and its spec).

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/angular-demo
git commit -m "chore(communication-protocols): scaffold the GraphQL Angular demo app"
```

---

### Task 2: Domain and auth models

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/core/graphql/graphql.models.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.models.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `ID`, `PageInfo`, `Edge<T>`, `Connection<T>`, `emptyConnection<T>()`, `Role`, `OrderStatus`, `Category`, `Product`, `Review`, `User`, `Order`, `OrderItem`, `ProductFilter`, `ReviewFilter`, `AddReviewInput`, `OrderItemInput`, `PlaceOrderInput` from `graphql.models.ts`; `AuthUser`, `Credentials` from `auth.models.ts`. Every later task importing domain types imports them from these two files.

These are plain TypeScript types with no runtime behavior, so there's no unit test to write — correctness is verified by the TypeScript compiler in Step 2.

- [ ] **Step 1: Write the GraphQL domain models**

`src/app/core/graphql/graphql.models.ts`:

```ts
export type ID = string;

export interface PageInfo {
  hasNextPage: boolean;
  endCursor: string | null;
}

export interface Edge<T> {
  node: T;
  cursor: string;
}

export interface Connection<T> {
  edges: Edge<T>[];
  pageInfo: PageInfo;
  totalCount: number;
}

export function emptyConnection<T>(): Connection<T> {
  return { edges: [], pageInfo: { hasNextPage: false, endCursor: null }, totalCount: 0 };
}

export type Role = 'CUSTOMER' | 'ADMIN';
export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface Category {
  id: ID;
  name: string;
  parent?: Category | null;
}

export interface Product {
  id: ID;
  name: string;
  priceCents: number;
  stockQty: number;
  categories?: Category[];
}

export interface User {
  id: ID;
  username: string;
  displayName: string;
  role: Role;
}

export interface Review {
  id: ID;
  productId: ID;
  author: User;
  rating: number;
  comment: string | null;
}

export interface OrderItem {
  id: ID;
  product: Product;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

export interface Order {
  id: ID;
  user: User;
  status: OrderStatus;
  placedAt: string;
  items: OrderItem[];
  totalCents: number;
}

export interface ProductFilter {
  nameContains?: string;
  minPriceCents?: number;
  maxPriceCents?: number;
}

export interface ReviewFilter {
  minRating?: number;
}

export interface AddReviewInput {
  productId: ID;
  rating: number;
  comment?: string;
}

export interface OrderItemInput {
  productId: ID;
  quantity: number;
}

export interface PlaceOrderInput {
  items: OrderItemInput[];
}
```

- [ ] **Step 2: Write the auth models and verify compilation**

`src/app/core/auth/auth.models.ts`:

```ts
import { Role } from '../graphql/graphql.models';

export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface Credentials {
  username: string;
  password: string;
}
```

Run: `npx tsc -p tsconfig.app.json --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add src/app/core/graphql/graphql.models.ts src/app/core/auth/auth.models.ts
git commit -m "feat(communication-protocols): add GraphQL angular-demo domain models"
```

---

### Task 3: AuthService

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.service.spec.ts`

**Interfaces:**
- Consumes: `AuthUser`, `Credentials` from `../auth/auth.models` (Task 2).
- Produces: `AuthService` (`providedIn: 'root'`) with `readonly currentUser: Signal<AuthUser | null>`, `readonly credentials: Signal<Credentials | null>`, `setSession(credentials: Credentials, user: AuthUser): void`, `logout(): void`. Every later task needing the logged-in user or Basic-auth credentials injects this service.

- [ ] **Step 1: Write the failing test**

`src/app/core/auth/auth.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { AuthUser, Credentials } from './auth.models';

describe('AuthService', () => {
  const credentials: Credentials = { username: 'user', password: 'userPassword' };
  const user: AuthUser = { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('starts with no session', () => {
    const service = TestBed.inject(AuthService);
    expect(service.currentUser()).toBeNull();
    expect(service.credentials()).toBeNull();
  });

  it('setSession stores the user and credentials, and persists them', () => {
    const service = TestBed.inject(AuthService);
    service.setSession(credentials, user);

    expect(service.currentUser()).toEqual(user);
    expect(service.credentials()).toEqual(credentials);
    expect(JSON.parse(sessionStorage.getItem('graphql-demo-auth')!)).toEqual({ credentials, user });
  });

  it('logout clears the session and storage', () => {
    const service = TestBed.inject(AuthService);
    service.setSession(credentials, user);

    service.logout();

    expect(service.currentUser()).toBeNull();
    expect(service.credentials()).toBeNull();
    expect(sessionStorage.getItem('graphql-demo-auth')).toBeNull();
  });

  it('hydrates an existing session from sessionStorage on construction', () => {
    sessionStorage.setItem('graphql-demo-auth', JSON.stringify({ credentials, user }));

    const service = TestBed.inject(AuthService);

    expect(service.currentUser()).toEqual(user);
    expect(service.credentials()).toEqual(credentials);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.service.spec.ts'`
Expected: FAIL — `auth.service` module not found.

- [ ] **Step 3: Implement AuthService**

`src/app/core/auth/auth.service.ts`:

```ts
import { Injectable, computed, signal } from '@angular/core';
import { AuthUser, Credentials } from './auth.models';

const STORAGE_KEY = 'graphql-demo-auth';

interface StoredSession {
  credentials: Credentials;
  user: AuthUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly session = signal<StoredSession | null>(readStoredSession());

  readonly currentUser = computed(() => this.session()?.user ?? null);
  readonly credentials = computed(() => this.session()?.credentials ?? null);

  setSession(credentials: Credentials, user: AuthUser): void {
    const stored: StoredSession = { credentials, user };
    this.session.set(stored);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  }

  logout(): void {
    this.session.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
  }
}

function readStoredSession(): StoredSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    return null;
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.service.spec.ts'`
Expected: PASS (4 specs).

- [ ] **Step 5: Commit**

```bash
git add src/app/core/auth/auth.service.ts src/app/core/auth/auth.service.spec.ts
git commit -m "feat(communication-protocols): add AuthService to the GraphQL angular-demo"
```

---

### Task 4: authInterceptor

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.interceptor.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.interceptor.spec.ts`

**Interfaces:**
- Consumes: `AuthService` (Task 3).
- Produces: `authInterceptor: HttpInterceptorFn`. Registered in `app.config.ts` (Task 7) via `provideHttpClient(withInterceptors([authInterceptor]))`.

- [ ] **Step 1: Write the failing test**

`src/app/core/auth/auth.interceptor.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('adds no Authorization header when there is no session', () => {
    http.post('/graphql', {}).subscribe();
    const req = httpMock.expectOne('/graphql');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('adds a Basic Authorization header from the current session', () => {
    authService.setSession(
      { username: 'user', password: 'userPassword' },
      { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    );

    http.post('/graphql', {}).subscribe();

    const req = httpMock.expectOne('/graphql');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('user:userPassword')}`);
    req.flush({});
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.interceptor.spec.ts'`
Expected: FAIL — `auth.interceptor` module not found.

- [ ] **Step 3: Implement the interceptor**

`src/app/core/auth/auth.interceptor.ts`:

```ts
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const credentials = inject(AuthService).credentials();
  if (!credentials) {
    return next(req);
  }
  const token = btoa(`${credentials.username}:${credentials.password}`);
  return next(req.clone({ setHeaders: { Authorization: `Basic ${token}` } }));
};
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.interceptor.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 5: Commit**

```bash
git add src/app/core/auth/auth.interceptor.ts src/app/core/auth/auth.interceptor.spec.ts
git commit -m "feat(communication-protocols): add Basic-auth HTTP interceptor to the GraphQL angular-demo"
```

---

### Task 5: authGuard

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.guard.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/core/auth/auth.guard.spec.ts`

**Interfaces:**
- Consumes: `AuthService` (Task 3).
- Produces: `authGuard: CanActivateFn`. Applied to every protected route in `app.routes.ts` (Task 7).

- [ ] **Step 1: Write the failing test**

`src/app/core/auth/auth.guard.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('allows navigation when a user is logged in', () => {
    const authService = TestBed.inject(AuthService);
    authService.setSession(
      { username: 'user', password: 'userPassword' },
      { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    );

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result).toBe(true);
  });

  it('redirects to /login when no user is logged in', () => {
    const router = TestBed.inject(Router);

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result).toEqual(router.parseUrl('/login'));
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.guard.spec.ts'`
Expected: FAIL — `auth.guard` module not found.

- [ ] **Step 3: Implement the guard**

`src/app/core/auth/auth.guard.ts`:

```ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  return authService.currentUser() ? true : router.parseUrl('/login');
};
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.guard.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 5: Commit**

```bash
git add src/app/core/auth/auth.guard.ts src/app/core/auth/auth.guard.spec.ts
git commit -m "feat(communication-protocols): add route auth guard to the GraphQL angular-demo"
```

---

### Task 6: GraphQL error classification

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/core/graphql/error-handling.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/core/graphql/error-handling.spec.ts`

**Interfaces:**
- Consumes: nothing (operates on the `graphql` package's `GraphQLFormattedError` shape — `{ message, extensions }` — which is what both `DemoExceptionResolver`'s `errors[]` entries and Apollo Client's `CombinedGraphQLErrors.errors` carry).
- Produces: `ErrorClassification` (`'UNAUTHORIZED' | 'FORBIDDEN' | 'BAD_REQUEST' | 'INTERNAL_ERROR' | 'UNKNOWN'`), `classifyGraphQlError(error): ErrorClassification`, `ErrorHandlerDeps` (`{ logout, navigateToLogin, showMessage }`), `handleGraphQlErrors(errors, deps): void`. Consumed by `apollo.provider.ts` (Task 7).

- [ ] **Step 1: Write the failing test**

`src/app/core/graphql/error-handling.spec.ts`:

```ts
import { GraphQLFormattedError } from 'graphql';
import { classifyGraphQlError, ErrorHandlerDeps, handleGraphQlErrors } from './error-handling';

function errorWith(classification: string | undefined, message = 'boom'): GraphQLFormattedError {
  return { message, extensions: classification === undefined ? undefined : { classification } };
}

describe('classifyGraphQlError', () => {
  it('classifies known extensions.classification values', () => {
    expect(classifyGraphQlError(errorWith('UNAUTHORIZED'))).toBe('UNAUTHORIZED');
    expect(classifyGraphQlError(errorWith('FORBIDDEN'))).toBe('FORBIDDEN');
    expect(classifyGraphQlError(errorWith('BAD_REQUEST'))).toBe('BAD_REQUEST');
    expect(classifyGraphQlError(errorWith('INTERNAL_ERROR'))).toBe('INTERNAL_ERROR');
  });

  it('falls back to UNKNOWN when extensions.classification is missing or unrecognized', () => {
    expect(classifyGraphQlError(errorWith(undefined))).toBe('UNKNOWN');
    expect(classifyGraphQlError(errorWith('SOMETHING_ELSE'))).toBe('UNKNOWN');
  });
});

describe('handleGraphQlErrors', () => {
  let deps: jasmine.SpyObj<ErrorHandlerDeps>;

  beforeEach(() => {
    deps = jasmine.createSpyObj<ErrorHandlerDeps>(['logout', 'navigateToLogin', 'showMessage']);
  });

  it('logs out, navigates to /login, and shows a message on UNAUTHORIZED', () => {
    handleGraphQlErrors([errorWith('UNAUTHORIZED', 'not authenticated')], deps);

    expect(deps.logout).toHaveBeenCalled();
    expect(deps.navigateToLogin).toHaveBeenCalled();
    expect(deps.showMessage).toHaveBeenCalledWith('Session expired — please log in again.');
  });

  it('shows a message without logging out on FORBIDDEN', () => {
    handleGraphQlErrors([errorWith('FORBIDDEN', 'not your order')], deps);

    expect(deps.logout).not.toHaveBeenCalled();
    expect(deps.navigateToLogin).not.toHaveBeenCalled();
    expect(deps.showMessage).toHaveBeenCalledWith('Not allowed: not your order');
  });

  it('shows a generic message for other classifications', () => {
    handleGraphQlErrors([errorWith('INTERNAL_ERROR', 'simulated failure')], deps);

    expect(deps.showMessage).toHaveBeenCalledWith('Error: simulated failure');
  });

  it('handles every error in the list', () => {
    handleGraphQlErrors([errorWith('FORBIDDEN', 'a'), errorWith('BAD_REQUEST', 'b')], deps);

    expect(deps.showMessage).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/error-handling.spec.ts'`
Expected: FAIL — `error-handling` module not found.

- [ ] **Step 3: Implement error classification**

`src/app/core/graphql/error-handling.ts`:

```ts
import { GraphQLFormattedError } from 'graphql';

export type ErrorClassification = 'UNAUTHORIZED' | 'FORBIDDEN' | 'BAD_REQUEST' | 'INTERNAL_ERROR' | 'UNKNOWN';

const KNOWN_CLASSIFICATIONS: readonly string[] = ['UNAUTHORIZED', 'FORBIDDEN', 'BAD_REQUEST', 'INTERNAL_ERROR'];

export function classifyGraphQlError(error: GraphQLFormattedError): ErrorClassification {
  const classification = error.extensions?.['classification'];
  return typeof classification === 'string' && KNOWN_CLASSIFICATIONS.includes(classification)
    ? (classification as ErrorClassification)
    : 'UNKNOWN';
}

export interface ErrorHandlerDeps {
  logout: () => void;
  navigateToLogin: () => void;
  showMessage: (message: string) => void;
}

export function handleGraphQlErrors(errors: readonly GraphQLFormattedError[], deps: ErrorHandlerDeps): void {
  for (const error of errors) {
    const classification = classifyGraphQlError(error);
    if (classification === 'UNAUTHORIZED') {
      deps.logout();
      deps.navigateToLogin();
      deps.showMessage('Session expired — please log in again.');
    } else if (classification === 'FORBIDDEN') {
      deps.showMessage(`Not allowed: ${error.message}`);
    } else {
      deps.showMessage(`Error: ${error.message}`);
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/error-handling.spec.ts'`
Expected: PASS (7 specs).

- [ ] **Step 5: Commit**

```bash
git add src/app/core/graphql/error-handling.ts src/app/core/graphql/error-handling.spec.ts
git commit -m "feat(communication-protocols): add GraphQL error classification to the angular-demo"
```

---

### Task 7: Apollo wiring, app shell, routes skeleton

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/core/graphql/apollo.provider.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/core/graphql/apollo.provider.spec.ts`
- Modify: `communication-protocols/graphql/angular-demo/src/app/app.config.ts`
- Modify: `communication-protocols/graphql/angular-demo/src/app/app.routes.ts` (create if the scaffold didn't generate one — Task 1 used `--routing=false`)
- Modify: `communication-protocols/graphql/angular-demo/src/app/app.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/app.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/app.spec.ts`

**Interfaces:**
- Consumes: `AuthService` (Task 3), `handleGraphQlErrors` (Task 6), `authInterceptor` (Task 4), `authGuard` (Task 5).
- Produces: `createApollo()` (the `provideApollo` factory); `routes: Routes` with `/login` and placeholder-free guarded top-level paths `catalog`, `categories`, `live`, `orders` that later tasks fill in via `loadChildren`/`loadComponent`; `App` root component rendering a `mat-toolbar` nav (visible only when logged in) + `router-outlet`.

- [ ] **Step 1: Write the failing test for Apollo wiring**

`src/app/core/graphql/apollo.provider.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { InMemoryCache } from '@apollo/client';
import { createApollo } from './apollo.provider';

describe('createApollo', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([]), provideNoopAnimations()],
    });
  });

  it('returns Apollo client options with a link and an InMemoryCache', () => {
    const options = TestBed.runInInjectionContext(() => createApollo());

    expect(options.link).toBeTruthy();
    expect(options.cache instanceof InMemoryCache).toBe(true);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/apollo.provider.spec.ts'`
Expected: FAIL — `apollo.provider` module not found.

- [ ] **Step 3: Implement Apollo wiring**

`src/app/core/graphql/apollo.provider.ts`:

```ts
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpLink } from 'apollo-angular/http';
import { ApolloLink, CombinedGraphQLErrors, InMemoryCache, from } from '@apollo/client';
import { ErrorLink } from '@apollo/client/link/error';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { createClient } from 'graphql-ws';
import { OperationTypeNode } from 'graphql';
import { AuthService } from '../auth/auth.service';
import { handleGraphQlErrors } from './error-handling';

export function createApollo() {
  const httpLink = inject(HttpLink);
  const authService = inject(AuthService);
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);

  const http = httpLink.create({ uri: '/graphql' });

  const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsLink = new GraphQLWsLink(createClient({ url: `${wsProtocol}//${location.host}/graphql` }));

  const errorLink = new ErrorLink(({ error }) => {
    if (CombinedGraphQLErrors.is(error)) {
      handleGraphQlErrors(error.errors, {
        logout: () => authService.logout(),
        navigateToLogin: () => router.navigateByUrl('/login'),
        showMessage: (message) => snackBar.open(message, 'Dismiss', { duration: 5000 }),
      });
    }
  });

  const link = ApolloLink.split(
    ({ operationType }) => operationType === OperationTypeNode.SUBSCRIPTION,
    wsLink,
    from([errorLink, http]),
  );

  return {
    link,
    cache: new InMemoryCache(),
  };
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/apollo.provider.spec.ts'`
Expected: PASS.

- [ ] **Step 5: Wire app.config.ts**

`src/app/app.config.ts`:

```ts
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideApollo } from 'apollo-angular';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { createApollo } from './core/graphql/apollo.provider';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideApollo(createApollo),
  ],
};
```

- [ ] **Step 6: Create the routes skeleton**

`src/app/app.routes.ts`:

```ts
import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'catalog' },
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: 'catalog',
    canActivate: [authGuard],
    loadChildren: () => import('./features/catalog/catalog.routes').then((m) => m.CATALOG_ROUTES),
  },
  {
    path: 'categories',
    canActivate: [authGuard],
    loadComponent: () => import('./features/categories/category-tree').then((m) => m.CategoryTree),
  },
  {
    path: 'live',
    canActivate: [authGuard],
    loadComponent: () => import('./features/live-reviews/live-reviews').then((m) => m.LiveReviews),
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    loadChildren: () => import('./features/orders/orders.routes').then((m) => m.ORDERS_ROUTES),
  },
];
```

This won't compile yet — the imported feature modules don't exist. That's expected; Steps 7-8 add the shell (which doesn't reference these routes directly), and Tasks 8-20 add each feature in turn. Confirm with `npx tsc -p tsconfig.app.json --noEmit` only after Task 8 (login) makes `/login` resolvable; for now, proceed to the shell.

- [ ] **Step 7: Write the failing test for the app shell**

`src/app/app.spec.ts` (replace the generated default):

```ts
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideNoopAnimations()],
    });
  });

  it('creates the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('hides the nav when no user is logged in', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-links"]')).toBeNull();
  });

  it('shows the nav and the display name once a user is logged in', () => {
    const authService = TestBed.inject(AuthService);
    authService.setSession(
      { username: 'user', password: 'userPassword' },
      { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    );

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-links"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Demo User');
  });
});
```

- [ ] **Step 8: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/app.spec.ts'`
Expected: FAIL (the old default template doesn't have `[data-testid="nav-links"]` or use `AuthService`).

- [ ] **Step 9: Implement the shell**

`src/app/app.ts`:

```ts
import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet, MatToolbarModule, MatButtonModule],
  templateUrl: './app.html',
})
export class App {
  protected readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout();
  }
}
```

`src/app/app.html`:

```html
<mat-toolbar color="primary">
  <span>GraphQL Angular Demo</span>
  @if (authService.currentUser(); as user) {
    <nav data-testid="nav-links">
      <a mat-button routerLink="/catalog">Catalog</a>
      <a mat-button routerLink="/categories">Categories</a>
      <a mat-button routerLink="/live">Live Reviews</a>
      <a mat-button routerLink="/orders">Orders</a>
    </nav>
    <span class="spacer"></span>
    <span data-testid="current-user">{{ user.displayName }} ({{ user.role }})</span>
    <button mat-button (click)="logout()" data-testid="logout-button">Log out</button>
  }
</mat-toolbar>
<router-outlet />
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/app.spec.ts'`
Expected: PASS (3 specs).

- [ ] **Step 11: Commit**

```bash
git add src/app/core/graphql/apollo.provider.ts src/app/core/graphql/apollo.provider.spec.ts \
  src/app/app.config.ts src/app/app.routes.ts src/app/app.ts src/app/app.html src/app/app.spec.ts
git commit -m "feat(communication-protocols): wire Apollo, routes, and the app shell for the GraphQL angular-demo"
```

Note: `src/app/app.scss` from the scaffold can stay as-is or be emptied; it isn't referenced by the `templateUrl`-only component above unless you keep a `styleUrl` — if the scaffold's `app.ts` had a `styleUrl: './app.scss'`, drop it, since the component above has none.

---

### Task 8: Login feature

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/login/login.gql.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/login/login.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/login/login.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/login/login.spec.ts`

**Interfaces:**
- Consumes: `AuthService` (Task 3), `AuthUser` (Task 2), apollo-angular's `Apollo` service.
- Produces: `Login` component, routed at `/login` (already wired in Task 7). No other task depends on its internals.

This is the one place credentials are sent before a session exists, so the `me` query is issued with an explicit per-request `Authorization` header via Apollo's `context.headers` (the `authInterceptor` from Task 4 only adds a header once `AuthService.credentials()` is set, which happens *after* this query succeeds).

- [ ] **Step 1: Write the GraphQL document**

`src/app/features/login/login.gql.ts`:

```ts
import { gql } from 'apollo-angular';

export const ME_QUERY = gql`
  query Me {
    me {
      id
      username
      displayName
      role
    }
  }
`;
```

- [ ] **Step 2: Write the failing test**

`src/app/features/login/login.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Apollo } from 'apollo-angular';
import { of, throwError } from 'rxjs';
import { Login } from './login';
import { AuthService } from '../../core/auth/auth.service';

describe('Login', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    apollo = jasmine.createSpyObj<Apollo>(['query']);
    TestBed.configureTestingModule({
      imports: [Login, ReactiveFormsModule],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: Apollo, useValue: apollo }],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('quick-selecting a role logs in and navigates to /catalog', () => {
    const user = { id: '2', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' as const };
    apollo.query.and.returnValue(of({ data: { me: user } }) as never);
    spyOn(router, 'navigateByUrl');

    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.quickSelect('admin', 'adminPassword');

    expect(apollo.query).toHaveBeenCalledWith(
      jasmine.objectContaining({
        context: { headers: { Authorization: `Basic ${btoa('admin:adminPassword')}` } },
      }),
    );
    expect(authService.currentUser()).toEqual(user);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/catalog');
  });

  it('shows an error message when the credentials are rejected', () => {
    apollo.query.and.returnValue(throwError(() => new Error('unauthorized')) as never);

    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.quickSelect('user', 'wrong');
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('Invalid username or password.');
    expect(authService.currentUser()).toBeNull();
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/login.spec.ts'`
Expected: FAIL — `./login` module not found.

- [ ] **Step 4: Implement the component**

`src/app/features/login/login.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthUser } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ME_QUERY } from './login.gql';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly apollo = inject(Apollo);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  quickSelect(username: string, password: string): void {
    this.form.setValue({ username, password });
    this.submit();
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    const token = btoa(`${username}:${password}`);

    this.apollo
      .query<{ me: AuthUser }>({
        query: ME_QUERY,
        context: { headers: { Authorization: `Basic ${token}` } },
        fetchPolicy: 'network-only',
      })
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          this.authService.setSession({ username, password }, result.data.me);
          this.router.navigateByUrl('/catalog');
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Invalid username or password.');
        },
      });
  }
}
```

`src/app/features/login/login.html`:

```html
<mat-card>
  <mat-card-title>Log in</mat-card-title>
  <mat-card-content>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-form-field appearance="outline">
        <mat-label>Username</mat-label>
        <input matInput formControlName="username" data-testid="username-input" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Password</mat-label>
        <input matInput type="password" formControlName="password" data-testid="password-input" />
      </mat-form-field>
      <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || loading()" data-testid="login-submit">
        Log in
      </button>
    </form>
    <div class="quick-select">
      <button mat-stroked-button type="button" (click)="quickSelect('user', 'userPassword')" data-testid="quick-select-user">
        Continue as user
      </button>
      <button mat-stroked-button type="button" (click)="quickSelect('admin', 'adminPassword')" data-testid="quick-select-admin">
        Continue as admin
      </button>
    </div>
    @if (error(); as message) {
      <p data-testid="login-error">{{ message }}</p>
    }
  </mat-card-content>
</mat-card>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/login.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 6: Verify the app still compiles**

Run: `npx tsc -p tsconfig.app.json --noEmit`
Expected: still fails only on the not-yet-created `catalog.routes`/`category-tree`/`live-reviews`/`orders.routes` imports in `app.routes.ts` — that's expected until Tasks 11-20 add them.

- [ ] **Step 7: Commit**

```bash
git add src/app/features/login
git commit -m "feat(communication-protocols): add the login feature to the GraphQL angular-demo"
```

---

### Task 9: Shared ConnectionPaginator component

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/shared/connection-paginator/connection-paginator.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/shared/connection-paginator/connection-paginator.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/shared/connection-paginator/connection-paginator.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `ConnectionPaginator` standalone component, selector `app-connection-paginator`, inputs `hasNextPage: boolean` (required), `totalCount: number` (required), `loadedCount: number` (required), `loading: boolean` (default `false`), output `loadMore: void`. Used by the catalog, categories, and orders list components (Tasks 11, 14, 19).

- [ ] **Step 1: Write the failing test**

`src/app/shared/connection-paginator/connection-paginator.spec.ts`:

```ts
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConnectionPaginator } from './connection-paginator';

@Component({
  imports: [ConnectionPaginator],
  template: `
    <app-connection-paginator
      [hasNextPage]="hasNextPage"
      [totalCount]="10"
      [loadedCount]="3"
      (loadMore)="loadMoreCount = loadMoreCount + 1"
    />
  `,
})
class HostComponent {
  hasNextPage = true;
  loadMoreCount = 0;
}

describe('ConnectionPaginator', () => {
  it('shows the loaded/total summary', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const summary = fixture.nativeElement.querySelector('[data-testid="paginator-summary"]');
    expect(summary.textContent).toContain('3');
    expect(summary.textContent).toContain('10');
  });

  it('emits loadMore when the button is clicked and hasNextPage is true', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="load-more-button"]');

    button.click();

    expect(fixture.componentInstance.loadMoreCount).toBe(1);
  });

  it('disables the button when hasNextPage is false', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.hasNextPage = false;
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="load-more-button"]');

    expect(button.disabled).toBe(true);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/connection-paginator.spec.ts'`
Expected: FAIL — `./connection-paginator` module not found.

- [ ] **Step 3: Implement the component**

`src/app/shared/connection-paginator/connection-paginator.ts`:

```ts
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-connection-paginator',
  imports: [MatButtonModule],
  templateUrl: './connection-paginator.html',
})
export class ConnectionPaginator {
  readonly hasNextPage = input.required<boolean>();
  readonly totalCount = input.required<number>();
  readonly loadedCount = input.required<number>();
  readonly loading = input(false);
  readonly loadMore = output<void>();
}
```

`src/app/shared/connection-paginator/connection-paginator.html`:

```html
<div class="connection-paginator">
  <span data-testid="paginator-summary">Showing {{ loadedCount() }} of {{ totalCount() }}</span>
  <button
    mat-stroked-button
    type="button"
    data-testid="load-more-button"
    [disabled]="!hasNextPage() || loading()"
    (click)="loadMore.emit()"
  >
    Load more
  </button>
</div>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/connection-paginator.spec.ts'`
Expected: PASS (3 specs).

- [ ] **Step 5: Commit**

```bash
git add src/app/shared/connection-paginator
git commit -m "feat(communication-protocols): add shared connection paginator to the GraphQL angular-demo"
```

---

### Task 10: ProductCatalogService

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/catalog.gql.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-catalog.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-catalog.service.spec.ts`

**Interfaces:**
- Consumes: `Connection`, `Product`, `ProductFilter`, `Review`, `ReviewFilter`, `AddReviewInput`, `emptyConnection` (Task 2), apollo-angular's `Apollo`.
- Produces: `ProductCatalogService` (`providedIn: 'root'`) with `listProducts(filter: ProductFilter | null, first: number, after: string | null): Observable<Connection<Product>>`, `getProduct(id: string): Observable<Product | null>`, `listReviews(productId: string, filter: ReviewFilter | null, first: number, after: string | null): Observable<Connection<Review>>`, `addReview(input: AddReviewInput): Observable<Review>`, `deleteReview(id: string): Observable<boolean>`. Consumed by `ProductList` and `ProductDetail` (Tasks 11-12).

- [ ] **Step 1: Write the GraphQL documents**

`src/app/features/catalog/catalog.gql.ts`:

```ts
import { gql } from 'apollo-angular';

export const PRODUCTS_QUERY = gql`
  query Products($filter: ProductFilter, $first: Int, $after: String) {
    products(filter: $filter, first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        cursor
        node {
          id
          name
          priceCents
          stockQty
          categories {
            id
            name
          }
        }
      }
    }
  }
`;

export const PRODUCT_QUERY = gql`
  query Product($id: ID!) {
    product(id: $id) {
      id
      name
      priceCents
      stockQty
      categories {
        id
        name
      }
    }
  }
`;

export const PRODUCT_REVIEWS_QUERY = gql`
  query ProductReviews($id: ID!, $filter: ReviewFilter, $first: Int, $after: String) {
    product(id: $id) {
      id
      reviews(filter: $filter, first: $first, after: $after) {
        totalCount
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          cursor
          node {
            id
            productId
            rating
            comment
            author {
              id
              displayName
            }
          }
        }
      }
    }
  }
`;

export const ADD_REVIEW_MUTATION = gql`
  mutation AddReview($input: AddReviewInput!) {
    addReview(input: $input) {
      id
      productId
      rating
      comment
      author {
        id
        displayName
      }
    }
  }
`;

export const DELETE_REVIEW_MUTATION = gql`
  mutation DeleteReview($id: ID!) {
    deleteReview(id: $id)
  }
`;
```

- [ ] **Step 2: Write the failing test**

`src/app/features/catalog/product-catalog.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { ProductCatalogService } from './product-catalog.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductCatalogService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: ProductCatalogService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery', 'mutate']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(ProductCatalogService);
  });

  it('listProducts maps the products connection', (done) => {
    const connection: Connection<Product> = { ...emptyConnection<Product>(), totalCount: 1 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { products: connection } }) } as never);

    service.listProducts(null, 20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('getProduct maps a single product', (done) => {
    const product: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { product } }) } as never);

    service.getProduct('1').subscribe((result) => {
      expect(result).toEqual(product);
      done();
    });
  });

  it('listReviews maps the nested reviews connection, defaulting to empty when the product is missing', (done) => {
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { product: null } }) } as never);

    service.listReviews('1', null, 20, null).subscribe((result) => {
      expect(result).toEqual(emptyConnection<Review>());
      done();
    });
  });

  it('addReview maps the created review', (done) => {
    const review: Review = { id: '9', productId: '1', author: { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' }, rating: 5, comment: 'Great' };
    apollo.mutate.and.returnValue(of({ data: { addReview: review } }) as never);

    service.addReview({ productId: '1', rating: 5, comment: 'Great' }).subscribe((result) => {
      expect(result).toEqual(review);
      done();
    });
  });

  it('deleteReview maps the boolean result', (done) => {
    apollo.mutate.and.returnValue(of({ data: { deleteReview: true } }) as never);

    service.deleteReview('9').subscribe((result) => {
      expect(result).toBe(true);
      done();
    });
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/product-catalog.service.spec.ts'`
Expected: FAIL — `./product-catalog.service` module not found.

- [ ] **Step 4: Implement the service**

`src/app/features/catalog/product-catalog.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { AddReviewInput, Connection, Product, ProductFilter, Review, ReviewFilter, emptyConnection } from '../../core/graphql/graphql.models';
import { ADD_REVIEW_MUTATION, DELETE_REVIEW_MUTATION, PRODUCT_QUERY, PRODUCT_REVIEWS_QUERY, PRODUCTS_QUERY } from './catalog.gql';

@Injectable({ providedIn: 'root' })
export class ProductCatalogService {
  private readonly apollo = inject(Apollo);

  listProducts(filter: ProductFilter | null, first: number, after: string | null): Observable<Connection<Product>> {
    return this.apollo
      .watchQuery<{ products: Connection<Product> }>({
        query: PRODUCTS_QUERY,
        variables: { filter, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.products));
  }

  getProduct(id: string): Observable<Product | null> {
    return this.apollo
      .watchQuery<{ product: Product | null }>({ query: PRODUCT_QUERY, variables: { id }, fetchPolicy: 'network-only' })
      .valueChanges.pipe(map((result) => result.data.product));
  }

  listReviews(
    productId: string,
    filter: ReviewFilter | null,
    first: number,
    after: string | null,
  ): Observable<Connection<Review>> {
    return this.apollo
      .watchQuery<{ product: { reviews: Connection<Review> } | null }>({
        query: PRODUCT_REVIEWS_QUERY,
        variables: { id: productId, filter, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.product?.reviews ?? emptyConnection<Review>()));
  }

  addReview(input: AddReviewInput): Observable<Review> {
    return this.apollo
      .mutate<{ addReview: Review }>({ mutation: ADD_REVIEW_MUTATION, variables: { input } })
      .pipe(map((result) => result.data!.addReview));
  }

  deleteReview(id: string): Observable<boolean> {
    return this.apollo
      .mutate<{ deleteReview: boolean }>({ mutation: DELETE_REVIEW_MUTATION, variables: { id } })
      .pipe(map((result) => result.data!.deleteReview));
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/product-catalog.service.spec.ts'`
Expected: PASS (5 specs).

- [ ] **Step 6: Commit**

```bash
git add src/app/features/catalog/catalog.gql.ts src/app/features/catalog/product-catalog.service.ts \
  src/app/features/catalog/product-catalog.service.spec.ts
git commit -m "feat(communication-protocols): add ProductCatalogService to the GraphQL angular-demo"
```

---

### Task 11: ProductList component + catalog routes

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-list.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-list.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-list.spec.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/catalog.routes.ts` (placeholder-free once Task 12 adds `product-detail`; for this task it only routes to `ProductList`)

**Interfaces:**
- Consumes: `ProductCatalogService` (Task 10), `ConnectionPaginator` (Task 9), `Product`, `ProductFilter`, `Edge` (Task 2).
- Produces: `ProductList` component (`app-product-list`). `CATALOG_ROUTES` (extended by Task 12 with the detail route).

- [ ] **Step 1: Write the failing test**

`src/app/features/catalog/product-list.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ProductList } from './product-list';
import { ProductCatalogService } from './product-catalog.service';
import { Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductList', () => {
  let service: jasmine.SpyObj<ProductCatalogService>;

  const page1: Connection<Product> = {
    edges: [{ cursor: 'c1', node: { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 } }],
    pageInfo: { hasNextPage: true, endCursor: 'c1' },
    totalCount: 2,
  };
  const page2: Connection<Product> = {
    edges: [{ cursor: 'c2', node: { id: '2', name: 'Gadget', priceCents: 900, stockQty: 5 } }],
    pageInfo: { hasNextPage: false, endCursor: 'c2' },
    totalCount: 2,
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<ProductCatalogService>(['listProducts']);
    service.listProducts.and.returnValue(of(page1));
    TestBed.configureTestingModule({
      imports: [ProductList, RouterModule],
      providers: [provideRouter([]), { provide: ProductCatalogService, useValue: service }],
    });
  });

  it('loads the first page on init', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();

    expect(service.listProducts).toHaveBeenCalledWith(null, 20, null);
    expect(fixture.componentInstance.edges().length).toBe(1);
    expect(fixture.componentInstance.totalCount()).toBe(2);
  });

  it('loading more appends the next page using the current end cursor', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    service.listProducts.and.returnValue(of(page2));

    fixture.componentInstance.loadMore();

    expect(service.listProducts).toHaveBeenCalledWith(null, 20, 'c1');
    expect(fixture.componentInstance.edges().length).toBe(2);
    expect(fixture.componentInstance.pageInfo().hasNextPage).toBe(false);
  });

  it('applying a filter resets the list and re-queries with the filter', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    service.listProducts.and.returnValue(of(page2));

    fixture.componentInstance.filterForm.setValue({ nameContains: 'Gadget', minPriceCents: null, maxPriceCents: null });
    fixture.componentInstance.search();

    expect(service.listProducts).toHaveBeenCalledWith({ nameContains: 'Gadget' }, 20, null);
    expect(fixture.componentInstance.edges().length).toBe(1);
    expect(fixture.componentInstance.edges()[0].node.name).toBe('Gadget');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/product-list.spec.ts'`
Expected: FAIL — `./product-list` module not found.

- [ ] **Step 3: Implement the component**

`src/app/features/catalog/product-list.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { Edge, PageInfo, Product, ProductFilter, emptyConnection } from '../../core/graphql/graphql.models';
import { ProductCatalogService } from './product-catalog.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-product-list',
  imports: [DecimalPipe, ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, ConnectionPaginator],
  templateUrl: './product-list.html',
})
export class ProductList {
  private readonly catalog = inject(ProductCatalogService);
  private readonly fb = inject(FormBuilder);

  readonly filterForm = this.fb.nonNullable.group({
    nameContains: this.fb.control<string | null>(null),
    minPriceCents: this.fb.control<number | null>(null),
    maxPriceCents: this.fb.control<number | null>(null),
  });

  readonly edges = signal<Edge<Product>[]>([]);
  readonly pageInfo = signal<PageInfo>(emptyConnection<Product>().pageInfo);
  readonly totalCount = signal(0);
  readonly loading = signal(false);

  constructor() {
    this.search();
  }

  search(): void {
    this.edges.set([]);
    this.loadPage(null);
  }

  loadMore(): void {
    this.loadPage(this.pageInfo().endCursor);
  }

  private loadPage(after: string | null): void {
    this.loading.set(true);
    this.catalog.listProducts(this.currentFilter(), PAGE_SIZE, after).subscribe((connection) => {
      this.loading.set(false);
      this.edges.set([...this.edges(), ...connection.edges]);
      this.pageInfo.set(connection.pageInfo);
      this.totalCount.set(connection.totalCount);
    });
  }

  private currentFilter(): ProductFilter | null {
    const raw = this.filterForm.getRawValue();
    const filter: ProductFilter = {};
    if (raw.nameContains) {
      filter.nameContains = raw.nameContains;
    }
    if (raw.minPriceCents != null) {
      filter.minPriceCents = raw.minPriceCents;
    }
    if (raw.maxPriceCents != null) {
      filter.maxPriceCents = raw.maxPriceCents;
    }
    return Object.keys(filter).length > 0 ? filter : null;
  }
}
```

`src/app/features/catalog/product-list.html`:

```html
<form [formGroup]="filterForm" (ngSubmit)="search()">
  <mat-form-field appearance="outline">
    <mat-label>Name contains</mat-label>
    <input matInput formControlName="nameContains" data-testid="filter-name" />
  </mat-form-field>
  <mat-form-field appearance="outline">
    <mat-label>Min price (cents)</mat-label>
    <input matInput type="number" formControlName="minPriceCents" data-testid="filter-min-price" />
  </mat-form-field>
  <mat-form-field appearance="outline">
    <mat-label>Max price (cents)</mat-label>
    <input matInput type="number" formControlName="maxPriceCents" data-testid="filter-max-price" />
  </mat-form-field>
  <button mat-raised-button color="primary" type="submit" data-testid="filter-submit">Filter</button>
</form>

<div class="product-grid">
  @for (edge of edges(); track edge.node.id) {
    <mat-card [routerLink]="[edge.node.id]" data-testid="product-card">
      <mat-card-title>{{ edge.node.name }}</mat-card-title>
      <mat-card-content>
        <p>{{ edge.node.priceCents / 100 | number: '1.2-2' }} — stock: {{ edge.node.stockQty }}</p>
      </mat-card-content>
    </mat-card>
  }
</div>

<app-connection-paginator
  [hasNextPage]="pageInfo().hasNextPage"
  [totalCount]="totalCount()"
  [loadedCount]="edges().length"
  [loading]="loading()"
  (loadMore)="loadMore()"
/>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/product-list.spec.ts'`
Expected: PASS (3 specs).

- [ ] **Step 5: Add catalog routes and wire them into the app**

`src/app/features/catalog/catalog.routes.ts`:

```ts
import { Routes } from '@angular/router';

export const CATALOG_ROUTES: Routes = [{ path: '', loadComponent: () => import('./product-list').then((m) => m.ProductList) }];
```

`app.routes.ts` (Task 7) already references `./features/catalog/catalog.routes` — no change needed there.

- [ ] **Step 6: Verify the app compiles further**

Run: `npx tsc -p tsconfig.app.json --noEmit`
Expected: errors remain only for the not-yet-created `category-tree`/`live-reviews`/`orders.routes` imports.

- [ ] **Step 7: Commit**

```bash
git add src/app/features/catalog/product-list.ts src/app/features/catalog/product-list.html \
  src/app/features/catalog/product-list.spec.ts src/app/features/catalog/catalog.routes.ts
git commit -m "feat(communication-protocols): add product list to the GraphQL angular-demo catalog"
```

---

### Task 12: ProductDetail + AddReviewDialog

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/add-review-dialog.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/add-review-dialog.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/catalog/add-review-dialog.spec.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/catalog/product-detail.spec.ts`
- Modify: `communication-protocols/graphql/angular-demo/src/app/features/catalog/catalog.routes.ts`

**Interfaces:**
- Consumes: `ProductCatalogService` (Task 10), `AuthService` (Task 3), `ConnectionPaginator` (Task 9), `Review`, `Product` (Task 2).
- Produces: `AddReviewDialog` component (opened via `MatDialog`, closes with `{ rating: number; comment: string | null } | undefined`); `ProductDetail` component routed at `catalog/:id`.

- [ ] **Step 1: Write the failing test for AddReviewDialog**

`src/app/features/catalog/add-review-dialog.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { AddReviewDialog } from './add-review-dialog';

describe('AddReviewDialog', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<AddReviewDialog>>;

  beforeEach(() => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<AddReviewDialog>>(['close']);
    TestBed.configureTestingModule({
      imports: [AddReviewDialog, ReactiveFormsModule],
      providers: [{ provide: MatDialogRef, useValue: dialogRef }],
    });
  });

  it('closes with the form value on submit', () => {
    const fixture = TestBed.createComponent(AddReviewDialog);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ rating: 4, comment: 'Pretty good' });
    fixture.componentInstance.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({ rating: 4, comment: 'Pretty good' });
  });

  it('closes with undefined on cancel', () => {
    const fixture = TestBed.createComponent(AddReviewDialog);
    fixture.detectChanges();

    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith(undefined);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/add-review-dialog.spec.ts'`
Expected: FAIL — `./add-review-dialog` module not found.

- [ ] **Step 3: Implement AddReviewDialog**

`src/app/features/catalog/add-review-dialog.ts`:

```ts
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-add-review-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './add-review-dialog.html',
})
export class AddReviewDialog {
  private readonly dialogRef = inject(MatDialogRef<AddReviewDialog>);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    rating: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
    comment: this.fb.control<string | null>(null),
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(this.form.getRawValue());
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
```

`src/app/features/catalog/add-review-dialog.html`:

```html
<h2 mat-dialog-title>Add a review</h2>
<form [formGroup]="form" (ngSubmit)="submit()">
  <mat-dialog-content>
    <mat-form-field appearance="outline">
      <mat-label>Rating (1-5)</mat-label>
      <input matInput type="number" formControlName="rating" data-testid="rating-input" />
    </mat-form-field>
    <mat-form-field appearance="outline">
      <mat-label>Comment</mat-label>
      <textarea matInput formControlName="comment" data-testid="comment-input"></textarea>
    </mat-form-field>
  </mat-dialog-content>
  <mat-dialog-actions>
    <button mat-button type="button" (click)="cancel()" data-testid="cancel-button">Cancel</button>
    <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid" data-testid="submit-button">
      Submit
    </button>
  </mat-dialog-actions>
</form>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/add-review-dialog.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 5: Write the failing test for ProductDetail**

`src/app/features/catalog/product-detail.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ProductDetail } from './product-detail';
import { ProductCatalogService } from './product-catalog.service';
import { AuthService } from '../../core/auth/auth.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('ProductDetail', () => {
  let catalog: jasmine.SpyObj<ProductCatalogService>;
  let authService: AuthService;

  const product: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10, categories: [] };
  const reviewsPage: Connection<Review> = {
    edges: [
      {
        cursor: 'r1',
        node: { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } },
      },
    ],
    pageInfo: { hasNextPage: false, endCursor: 'r1' },
    totalCount: 1,
  };

  beforeEach(() => {
    sessionStorage.clear();
    catalog = jasmine.createSpyObj<ProductCatalogService>(['getProduct', 'listReviews', 'deleteReview']);
    catalog.getProduct.and.returnValue(of(product));
    catalog.listReviews.and.returnValue(of(reviewsPage));
    TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductCatalogService, useValue: catalog },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
      ],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads the product and its reviews for the route id', () => {
    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(catalog.getProduct).toHaveBeenCalledWith('1');
    expect(catalog.listReviews).toHaveBeenCalledWith('1', null, 20, null);
    expect(fixture.componentInstance.product()).toEqual(product);
    expect(fixture.componentInstance.reviewEdges().length).toBe(1);
  });

  it('hides the delete-review action for a non-admin user', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="delete-review-9"]')).toBeNull();
  });

  it('shows and wires the delete-review action for an admin user', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });
    catalog.deleteReview.and.returnValue(of(true));

    const fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="delete-review-9"]');
    button.click();

    expect(catalog.deleteReview).toHaveBeenCalledWith('9');
  });
});
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/product-detail.spec.ts'`
Expected: FAIL — `./product-detail` module not found.

- [ ] **Step 7: Implement ProductDetail**

`src/app/features/catalog/product-detail.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { Edge, PageInfo, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';
import { AuthService } from '../../core/auth/auth.service';
import { ProductCatalogService } from './product-catalog.service';
import { AddReviewDialog } from './add-review-dialog';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-product-detail',
  imports: [DecimalPipe, MatCardModule, MatButtonModule, MatChipsModule, ConnectionPaginator],
  templateUrl: './product-detail.html',
})
export class ProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(ProductCatalogService);
  private readonly dialog = inject(MatDialog);
  protected readonly authService = inject(AuthService);

  private readonly productId = this.route.snapshot.paramMap.get('id')!;

  readonly product = signal<Product | null>(null);
  readonly reviewEdges = signal<Edge<Review>[]>([]);
  readonly reviewPageInfo = signal<PageInfo>(emptyConnection<Review>().pageInfo);
  readonly reviewTotalCount = signal(0);

  constructor() {
    this.catalog.getProduct(this.productId).subscribe((product) => this.product.set(product));
    this.loadReviews(null);
  }

  loadMoreReviews(): void {
    this.loadReviews(this.reviewPageInfo().endCursor);
  }

  openAddReviewDialog(): void {
    this.dialog
      .open(AddReviewDialog)
      .afterClosed()
      .subscribe((result: { rating: number; comment: string | null } | undefined) => {
        if (!result) {
          return;
        }
        this.catalog.addReview({ productId: this.productId, rating: result.rating, comment: result.comment ?? undefined }).subscribe((review) => {
          this.reviewEdges.set([{ cursor: review.id, node: review }, ...this.reviewEdges()]);
          this.reviewTotalCount.set(this.reviewTotalCount() + 1);
        });
      });
  }

  deleteReview(id: string): void {
    this.catalog.deleteReview(id).subscribe(() => {
      this.reviewEdges.set(this.reviewEdges().filter((edge) => edge.node.id !== id));
      this.reviewTotalCount.set(this.reviewTotalCount() - 1);
    });
  }

  private loadReviews(after: string | null): void {
    this.catalog.listReviews(this.productId, null, PAGE_SIZE, after).subscribe((connection) => {
      this.reviewEdges.set([...this.reviewEdges(), ...connection.edges]);
      this.reviewPageInfo.set(connection.pageInfo);
      this.reviewTotalCount.set(connection.totalCount);
    });
  }
}
```

`src/app/features/catalog/product-detail.html`:

```html
@if (product(); as p) {
  <mat-card>
    <mat-card-title>{{ p.name }}</mat-card-title>
    <mat-card-content>
      <p>{{ p.priceCents / 100 | number: '1.2-2' }} — stock: {{ p.stockQty }}</p>
      <mat-chip-set>
        @for (category of p.categories; track category.id) {
          <mat-chip>{{ category.name }}</mat-chip>
        }
      </mat-chip-set>
    </mat-card-content>
  </mat-card>
}

<button mat-raised-button color="primary" (click)="openAddReviewDialog()" data-testid="add-review-button">Add review</button>

<ul>
  @for (edge of reviewEdges(); track edge.node.id) {
    <li>
      <strong>{{ edge.node.rating }}/5</strong> by {{ edge.node.author.displayName }} — {{ edge.node.comment }}
      @if (authService.currentUser()?.role === 'ADMIN') {
        <button mat-button (click)="deleteReview(edge.node.id)" [attr.data-testid]="'delete-review-' + edge.node.id">
          Delete
        </button>
      }
    </li>
  }
</ul>

<app-connection-paginator
  [hasNextPage]="reviewPageInfo().hasNextPage"
  [totalCount]="reviewTotalCount()"
  [loadedCount]="reviewEdges().length"
  (loadMore)="loadMoreReviews()"
/>
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/product-detail.spec.ts'`
Expected: PASS (3 specs).

- [ ] **Step 9: Add the detail route**

`src/app/features/catalog/catalog.routes.ts`:

```ts
import { Routes } from '@angular/router';

export const CATALOG_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./product-list').then((m) => m.ProductList) },
  { path: ':id', loadComponent: () => import('./product-detail').then((m) => m.ProductDetail) },
];
```

- [ ] **Step 10: Commit**

```bash
git add src/app/features/catalog/add-review-dialog.ts src/app/features/catalog/add-review-dialog.html \
  src/app/features/catalog/add-review-dialog.spec.ts src/app/features/catalog/product-detail.ts \
  src/app/features/catalog/product-detail.html src/app/features/catalog/product-detail.spec.ts \
  src/app/features/catalog/catalog.routes.ts
git commit -m "feat(communication-protocols): add product detail and add-review dialog to the GraphQL angular-demo"
```

---

### Task 13: CategoryService

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/categories/categories.gql.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/categories/category.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/categories/category.service.spec.ts`

**Interfaces:**
- Consumes: `Connection`, `Category`, `Product`, `emptyConnection` (Task 2), apollo-angular's `Apollo`.
- Produces: `CategoryService` (`providedIn: 'root'`) with `listCategories(first: number, after: string | null): Observable<Connection<Category>>`, `listChildren(categoryId: string, first: number, after: string | null): Observable<Connection<Category>>`, `listProducts(categoryId: string, first: number, after: string | null): Observable<Connection<Product>>`. Consumed by `CategoryTree`/`CategoryNode` (Task 14).

- [ ] **Step 1: Write the GraphQL documents**

`src/app/features/categories/categories.gql.ts`:

```ts
import { gql } from 'apollo-angular';

export const CATEGORIES_QUERY = gql`
  query Categories($first: Int, $after: String) {
    categories(first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        cursor
        node {
          id
          name
        }
      }
    }
  }
`;

export const CATEGORY_CHILDREN_QUERY = gql`
  query CategoryChildren($id: ID!, $first: Int, $after: String) {
    category(id: $id) {
      id
      children(first: $first, after: $after) {
        totalCount
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          cursor
          node {
            id
            name
          }
        }
      }
    }
  }
`;

export const CATEGORY_PRODUCTS_QUERY = gql`
  query CategoryProducts($id: ID!, $first: Int, $after: String) {
    category(id: $id) {
      id
      products(first: $first, after: $after) {
        totalCount
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          cursor
          node {
            id
            name
            priceCents
            stockQty
          }
        }
      }
    }
  }
`;
```

- [ ] **Step 2: Write the failing test**

`src/app/features/categories/category.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { CategoryService } from './category.service';
import { Category, Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';

describe('CategoryService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: CategoryService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(CategoryService);
  });

  it('listCategories maps the root categories connection', (done) => {
    const connection: Connection<Category> = { ...emptyConnection<Category>(), totalCount: 3 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { categories: connection } }) } as never);

    service.listCategories(50, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('listChildren maps the nested children connection, defaulting to empty when the category is missing', (done) => {
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { category: null } }) } as never);

    service.listChildren('1', 50, null).subscribe((result) => {
      expect(result).toEqual(emptyConnection<Category>());
      done();
    });
  });

  it('listProducts maps the nested products connection', (done) => {
    const connection: Connection<Product> = { ...emptyConnection<Product>(), totalCount: 5 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { category: { id: '1', products: connection } } }) } as never);

    service.listProducts('1', 10, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/category.service.spec.ts'`
Expected: FAIL — `./category.service` module not found.

- [ ] **Step 4: Implement the service**

`src/app/features/categories/category.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { Category, Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';
import { CATEGORIES_QUERY, CATEGORY_CHILDREN_QUERY, CATEGORY_PRODUCTS_QUERY } from './categories.gql';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly apollo = inject(Apollo);

  listCategories(first: number, after: string | null): Observable<Connection<Category>> {
    return this.apollo
      .watchQuery<{ categories: Connection<Category> }>({
        query: CATEGORIES_QUERY,
        variables: { first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.categories));
  }

  listChildren(categoryId: string, first: number, after: string | null): Observable<Connection<Category>> {
    return this.apollo
      .watchQuery<{ category: { children: Connection<Category> } | null }>({
        query: CATEGORY_CHILDREN_QUERY,
        variables: { id: categoryId, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.category?.children ?? emptyConnection<Category>()));
  }

  listProducts(categoryId: string, first: number, after: string | null): Observable<Connection<Product>> {
    return this.apollo
      .watchQuery<{ category: { products: Connection<Product> } | null }>({
        query: CATEGORY_PRODUCTS_QUERY,
        variables: { id: categoryId, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.category?.products ?? emptyConnection<Product>()));
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/category.service.spec.ts'`
Expected: PASS (3 specs).

- [ ] **Step 6: Commit**

```bash
git add src/app/features/categories/categories.gql.ts src/app/features/categories/category.service.ts \
  src/app/features/categories/category.service.spec.ts
git commit -m "feat(communication-protocols): add CategoryService to the GraphQL angular-demo"
```

---

### Task 14: CategoryTree + CategoryNode

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/categories/category-node.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/categories/category-node.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/categories/category-node.spec.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/categories/category-tree.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/categories/category-tree.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/categories/category-tree.spec.ts`

**Interfaces:**
- Consumes: `CategoryService` (Task 13), `Category` (Task 2).
- Produces: `CategoryNode` (`app-category-node`, input `category: Category` required) — recurses into itself for children; `CategoryTree` (`app-category-tree`, routed at `/categories` per Task 7) — loads and renders the root categories via a list of `CategoryNode`s.

`CategoryNode` implements the tree recursively (no `MatTreeModule`/`CDK` tree control): each node lazily loads its own children and its own product list on demand, which is simpler to implement and test than wiring a `FlatTreeControl`, while still exercising the same `categoryChildren` DataLoader-batched field and the `category.products` nested connection.

- [ ] **Step 1: Write the failing test for CategoryNode**

`src/app/features/categories/category-node.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CategoryNode } from './category-node';
import { CategoryService } from './category.service';
import { Category, Connection, Product, emptyConnection } from '../../core/graphql/graphql.models';

describe('CategoryNode', () => {
  let service: jasmine.SpyObj<CategoryService>;
  const category: Category = { id: '1', name: 'Electronics' };

  beforeEach(() => {
    service = jasmine.createSpyObj<CategoryService>(['listChildren', 'listProducts']);
    TestBed.configureTestingModule({
      imports: [CategoryNode],
      providers: [provideNoopAnimations(), { provide: CategoryService, useValue: service }],
    });
  });

  it('loads children lazily the first time it is expanded', () => {
    const children: Connection<Category> = { ...emptyConnection<Category>(), edges: [{ cursor: 'c', node: { id: '2', name: 'Audio' } }] };
    service.listChildren.and.returnValue(of(children));

    const fixture = TestBed.createComponent(CategoryNode);
    fixture.componentRef.setInput('category', category);
    fixture.detectChanges();

    fixture.componentInstance.toggleExpanded();

    expect(service.listChildren).toHaveBeenCalledWith('1', 50, null);
    expect(fixture.componentInstance.children()).toEqual(children.edges.map((edge) => edge.node));
  });

  it('loads products lazily the first time they are shown', () => {
    const products: Connection<Product> = { ...emptyConnection<Product>(), edges: [{ cursor: 'p', node: { id: '9', name: 'Widget', priceCents: 500, stockQty: 1 } }] };
    service.listProducts.and.returnValue(of(products));

    const fixture = TestBed.createComponent(CategoryNode);
    fixture.componentRef.setInput('category', category);
    fixture.detectChanges();

    fixture.componentInstance.toggleProducts();

    expect(service.listProducts).toHaveBeenCalledWith('1', 10, null);
    expect(fixture.componentInstance.products()).toEqual(products.edges.map((edge) => edge.node));
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/category-node.spec.ts'`
Expected: FAIL — `./category-node` module not found.

- [ ] **Step 3: Implement CategoryNode**

`src/app/features/categories/category-node.ts`:

```ts
import { Component, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { Category, Product } from '../../core/graphql/graphql.models';
import { CategoryService } from './category.service';

@Component({
  selector: 'app-category-node',
  imports: [DecimalPipe, MatButtonModule, MatListModule, CategoryNode],
  templateUrl: './category-node.html',
})
export class CategoryNode {
  private readonly categoryService = inject(CategoryService);

  readonly category = input.required<Category>();

  readonly expanded = signal(false);
  readonly children = signal<Category[] | null>(null);

  readonly showingProducts = signal(false);
  readonly products = signal<Product[] | null>(null);

  toggleExpanded(): void {
    this.expanded.set(!this.expanded());
    if (this.expanded() && this.children() === null) {
      this.categoryService.listChildren(this.category().id, 50, null).subscribe((connection) => {
        this.children.set(connection.edges.map((edge) => edge.node));
      });
    }
  }

  toggleProducts(): void {
    this.showingProducts.set(!this.showingProducts());
    if (this.showingProducts() && this.products() === null) {
      this.categoryService.listProducts(this.category().id, 10, null).subscribe((connection) => {
        this.products.set(connection.edges.map((edge) => edge.node));
      });
    }
  }
}
```

`src/app/features/categories/category-node.html`:

```html
<div class="category-node">
  <button mat-button (click)="toggleExpanded()" [attr.data-testid]="'expand-' + category().id">
    {{ category().name }}
  </button>
  <button mat-button (click)="toggleProducts()" [attr.data-testid]="'products-' + category().id">Products</button>

  @if (showingProducts() && products(); as items) {
    <mat-nav-list>
      @for (product of items; track product.id) {
        <mat-list-item>{{ product.name }} — {{ product.priceCents / 100 | number: '1.2-2' }}</mat-list-item>
      }
    </mat-nav-list>
  }

  @if (expanded() && children(); as items) {
    <ul class="category-children">
      @for (child of items; track child.id) {
        <li>
          <app-category-node [category]="child" />
        </li>
      }
    </ul>
  }
</div>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/category-node.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 5: Write the failing test for CategoryTree**

`src/app/features/categories/category-tree.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CategoryTree } from './category-tree';
import { CategoryService } from './category.service';
import { Connection, Category } from '../../core/graphql/graphql.models';

describe('CategoryTree', () => {
  it('loads the root categories on init', () => {
    const service = jasmine.createSpyObj<CategoryService>(['listCategories']);
    const roots: Connection<Category> = {
      edges: [{ cursor: 'c', node: { id: '1', name: 'Electronics' } }],
      pageInfo: { hasNextPage: false, endCursor: 'c' },
      totalCount: 1,
    };
    service.listCategories.and.returnValue(of(roots));
    TestBed.configureTestingModule({
      imports: [CategoryTree],
      providers: [provideNoopAnimations(), { provide: CategoryService, useValue: service }],
    });

    const fixture = TestBed.createComponent(CategoryTree);
    fixture.detectChanges();

    expect(service.listCategories).toHaveBeenCalledWith(50, null);
    expect(fixture.componentInstance.rootCategories()).toEqual([{ id: '1', name: 'Electronics' }]);
    expect(fixture.nativeElement.querySelectorAll('app-category-node').length).toBe(1);
  });
});
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/category-tree.spec.ts'`
Expected: FAIL — `./category-tree` module not found.

- [ ] **Step 7: Implement CategoryTree**

`src/app/features/categories/category-tree.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { Category } from '../../core/graphql/graphql.models';
import { CategoryService } from './category.service';
import { CategoryNode } from './category-node';

@Component({
  selector: 'app-category-tree',
  imports: [CategoryNode],
  templateUrl: './category-tree.html',
})
export class CategoryTree {
  private readonly categoryService = inject(CategoryService);

  readonly rootCategories = signal<Category[]>([]);

  constructor() {
    this.categoryService.listCategories(50, null).subscribe((connection) => {
      this.rootCategories.set(connection.edges.map((edge) => edge.node));
    });
  }
}
```

`src/app/features/categories/category-tree.html`:

```html
<ul class="category-tree">
  @for (category of rootCategories(); track category.id) {
    <li>
      <app-category-node [category]="category" />
    </li>
  }
</ul>
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/category-tree.spec.ts'`
Expected: PASS (1 spec).

`app.routes.ts` (Task 7) already references `./features/categories/category-tree` — no change needed.

- [ ] **Step 9: Commit**

```bash
git add src/app/features/categories
git commit -m "feat(communication-protocols): add category tree browsing to the GraphQL angular-demo"
```

---

### Task 15: LiveReviewsService

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/live-reviews/live-reviews.gql.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/live-reviews/live-reviews.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/live-reviews/live-reviews.service.spec.ts`

**Interfaces:**
- Consumes: `Review` (Task 2), apollo-angular's `Apollo`.
- Produces: `LiveReviewsService` (`providedIn: 'root'`) with `subscribeToReviewAdded(productId: string | null): Observable<Review>`. Consumed by `LiveReviews` (Task 16).

- [ ] **Step 1: Write the GraphQL document**

`src/app/features/live-reviews/live-reviews.gql.ts`:

```ts
import { gql } from 'apollo-angular';

export const REVIEW_ADDED_SUBSCRIPTION = gql`
  subscription ReviewAdded($productId: ID) {
    reviewAdded(productId: $productId) {
      id
      productId
      rating
      comment
      author {
        id
        displayName
      }
    }
  }
`;
```

- [ ] **Step 2: Write the failing test**

`src/app/features/live-reviews/live-reviews.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { LiveReviewsService } from './live-reviews.service';
import { Review } from '../../core/graphql/graphql.models';

describe('LiveReviewsService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: LiveReviewsService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['subscribe']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(LiveReviewsService);
  });

  it('subscribes with the given productId and maps emitted reviews', (done) => {
    const review: Review = { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } };
    apollo.subscribe.and.returnValue(of({ data: { reviewAdded: review } }) as never);

    service.subscribeToReviewAdded('1').subscribe((result) => {
      expect(apollo.subscribe).toHaveBeenCalledWith(
        jasmine.objectContaining({ variables: { productId: '1' } }),
      );
      expect(result).toEqual(review);
      done();
    });
  });

  it('subscribes with a null productId to receive every review', (done) => {
    const review: Review = { id: '10', productId: '2', rating: 3, comment: null, author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } };
    apollo.subscribe.and.returnValue(of({ data: { reviewAdded: review } }) as never);

    service.subscribeToReviewAdded(null).subscribe((result) => {
      expect(apollo.subscribe).toHaveBeenCalledWith(jasmine.objectContaining({ variables: { productId: null } }));
      expect(result).toEqual(review);
      done();
    });
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/live-reviews.service.spec.ts'`
Expected: FAIL — `./live-reviews.service` module not found.

- [ ] **Step 4: Implement the service**

`src/app/features/live-reviews/live-reviews.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { Review } from '../../core/graphql/graphql.models';
import { REVIEW_ADDED_SUBSCRIPTION } from './live-reviews.gql';

@Injectable({ providedIn: 'root' })
export class LiveReviewsService {
  private readonly apollo = inject(Apollo);

  subscribeToReviewAdded(productId: string | null): Observable<Review> {
    return this.apollo
      .subscribe<{ reviewAdded: Review }>({ query: REVIEW_ADDED_SUBSCRIPTION, variables: { productId } })
      .pipe(
        map((result) => {
          if (!result.data) {
            throw new Error('reviewAdded subscription returned no data');
          }
          return result.data.reviewAdded;
        }),
      );
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/live-reviews.service.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 6: Commit**

```bash
git add src/app/features/live-reviews/live-reviews.gql.ts src/app/features/live-reviews/live-reviews.service.ts \
  src/app/features/live-reviews/live-reviews.service.spec.ts
git commit -m "feat(communication-protocols): add LiveReviewsService to the GraphQL angular-demo"
```

---

### Task 16: LiveReviews component

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/live-reviews/live-reviews.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/live-reviews/live-reviews.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/live-reviews/live-reviews.spec.ts`

**Interfaces:**
- Consumes: `LiveReviewsService` (Task 15), `ProductCatalogService` (Task 10, reused to populate the product-filter dropdown), `MatSnackBar`.
- Produces: `LiveReviews` component, routed at `/live` (already wired in Task 7).

- [ ] **Step 1: Write the failing test**

`src/app/features/live-reviews/live-reviews.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { LiveReviews } from './live-reviews';
import { LiveReviewsService } from './live-reviews.service';
import { ProductCatalogService } from '../catalog/product-catalog.service';
import { Connection, Product, Review, emptyConnection } from '../../core/graphql/graphql.models';

describe('LiveReviews', () => {
  let liveReviewsService: jasmine.SpyObj<LiveReviewsService>;
  let catalog: jasmine.SpyObj<ProductCatalogService>;

  const review: Review = { id: '9', productId: '1', rating: 5, comment: 'Great', author: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' } };
  const products: Connection<Product> = { ...emptyConnection<Product>(), edges: [{ cursor: 'p', node: { id: '1', name: 'Widget', priceCents: 500, stockQty: 1 } }] };

  beforeEach(() => {
    liveReviewsService = jasmine.createSpyObj<LiveReviewsService>(['subscribeToReviewAdded']);
    liveReviewsService.subscribeToReviewAdded.and.returnValue(of(review));
    catalog = jasmine.createSpyObj<ProductCatalogService>(['listProducts']);
    catalog.listProducts.and.returnValue(of(products));

    TestBed.configureTestingModule({
      imports: [LiveReviews],
      providers: [
        provideNoopAnimations(),
        { provide: LiveReviewsService, useValue: liveReviewsService },
        { provide: ProductCatalogService, useValue: catalog },
      ],
    });
  });

  it('subscribes to every product on init and appends incoming reviews to the feed', () => {
    const fixture = TestBed.createComponent(LiveReviews);
    fixture.detectChanges();

    expect(liveReviewsService.subscribeToReviewAdded).toHaveBeenCalledWith(null);
    expect(fixture.componentInstance.feed()).toEqual([review]);
  });

  it('resubscribes with the selected productId when the filter changes', () => {
    const fixture = TestBed.createComponent(LiveReviews);
    fixture.detectChanges();

    fixture.componentInstance.selectProduct('1');

    expect(liveReviewsService.subscribeToReviewAdded).toHaveBeenCalledWith('1');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/live-reviews.spec.ts'`
Expected: FAIL — `./live-reviews` module not found.

- [ ] **Step 3: Implement the component**

`src/app/features/live-reviews/live-reviews.ts`:

```ts
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';
import { Product, Review } from '../../core/graphql/graphql.models';
import { ProductCatalogService } from '../catalog/product-catalog.service';
import { LiveReviewsService } from './live-reviews.service';

@Component({
  selector: 'app-live-reviews',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, MatListModule],
  templateUrl: './live-reviews.html',
})
export class LiveReviews implements OnDestroy {
  private readonly liveReviewsService = inject(LiveReviewsService);
  private readonly catalog = inject(ProductCatalogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly productFilter = this.fb.nonNullable.control<string | null>(null);
  readonly products = signal<Product[]>([]);
  readonly feed = signal<Review[]>([]);

  private subscription: Subscription | null = null;

  constructor() {
    this.catalog.listProducts(null, 50, null).subscribe((connection) => {
      this.products.set(connection.edges.map((edge) => edge.node));
    });
    this.selectProduct(null);
  }

  selectProduct(productId: string | null): void {
    this.productFilter.setValue(productId);
    this.subscription?.unsubscribe();
    this.subscription = this.liveReviewsService.subscribeToReviewAdded(productId).subscribe((review) => {
      this.feed.set([review, ...this.feed()]);
      this.snackBar.open(`New review: ${review.rating}/5`, 'Dismiss', { duration: 3000 });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
```

`src/app/features/live-reviews/live-reviews.html`:

```html
<mat-form-field appearance="outline">
  <mat-label>Product</mat-label>
  <mat-select [value]="productFilter.value" (selectionChange)="selectProduct($event.value)" data-testid="product-filter">
    <mat-option [value]="null">All products</mat-option>
    @for (product of products(); track product.id) {
      <mat-option [value]="product.id">{{ product.name }}</mat-option>
    }
  </mat-select>
</mat-form-field>

<mat-nav-list data-testid="live-feed">
  @for (review of feed(); track review.id) {
    <mat-list-item>{{ review.rating }}/5 by {{ review.author.displayName }} — {{ review.comment }}</mat-list-item>
  }
</mat-nav-list>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/live-reviews.spec.ts'`
Expected: PASS (2 specs).

`app.routes.ts` (Task 7) already references `./features/live-reviews/live-reviews` — no change needed.

- [ ] **Step 5: Commit**

```bash
git add src/app/features/live-reviews/live-reviews.ts src/app/features/live-reviews/live-reviews.html \
  src/app/features/live-reviews/live-reviews.spec.ts
git commit -m "feat(communication-protocols): add the live reviews subscription feed to the GraphQL angular-demo"
```

---

### Task 17: CartService

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/cart.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/orders/cart.service.spec.ts`

**Interfaces:**
- Consumes: `Product`, `PlaceOrderInput` (Task 2).
- Produces: `CartService` (`providedIn: 'root'`) with `readonly lines: Signal<CartLine[]>`, `readonly totalCents: Signal<number>`, `add(product: Product, quantity?: number): void`, `updateQuantity(productId: string, quantity: number): void`, `remove(productId: string): void`, `clear(): void`, `toPlaceOrderInput(): PlaceOrderInput`. `CartLine` is `{ product: Product; quantity: number }`. Consumed by `PlaceOrder` (Task 19) and, optionally, `ProductList`/`ProductDetail` "add to cart" buttons.

- [ ] **Step 1: Write the failing test**

`src/app/features/orders/cart.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { Product } from '../../core/graphql/graphql.models';

describe('CartService', () => {
  let service: CartService;
  const widget: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };
  const gadget: Product = { id: '2', name: 'Gadget', priceCents: 900, stockQty: 5 };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CartService);
  });

  it('starts empty', () => {
    expect(service.lines()).toEqual([]);
    expect(service.totalCents()).toBe(0);
  });

  it('add() adds a new line, and adding the same product again increases its quantity', () => {
    service.add(widget);
    service.add(widget, 2);

    expect(service.lines()).toEqual([{ product: widget, quantity: 3 }]);
    expect(service.totalCents()).toBe(1500);
  });

  it('updateQuantity() changes a line quantity, and removes it when set to 0', () => {
    service.add(widget);
    service.add(gadget);

    service.updateQuantity('1', 5);
    expect(service.lines()).toContain({ product: widget, quantity: 5 });

    service.updateQuantity('1', 0);
    expect(service.lines()).toEqual([{ product: gadget, quantity: 1 }]);
  });

  it('remove() removes a line', () => {
    service.add(widget);
    service.add(gadget);

    service.remove('1');

    expect(service.lines()).toEqual([{ product: gadget, quantity: 1 }]);
  });

  it('clear() empties the cart', () => {
    service.add(widget);

    service.clear();

    expect(service.lines()).toEqual([]);
  });

  it('toPlaceOrderInput() maps lines to a PlaceOrderInput', () => {
    service.add(widget, 2);
    service.add(gadget, 1);

    expect(service.toPlaceOrderInput()).toEqual({
      items: [
        { productId: '1', quantity: 2 },
        { productId: '2', quantity: 1 },
      ],
    });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/cart.service.spec.ts'`
Expected: FAIL — `./cart.service` module not found.

- [ ] **Step 3: Implement CartService**

`src/app/features/orders/cart.service.ts`:

```ts
import { Injectable, computed, signal } from '@angular/core';
import { PlaceOrderInput, Product } from '../../core/graphql/graphql.models';

export interface CartLine {
  product: Product;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly lineMap = signal<Map<string, CartLine>>(new Map());

  readonly lines = computed(() => Array.from(this.lineMap().values()));
  readonly totalCents = computed(() => this.lines().reduce((sum, line) => sum + line.product.priceCents * line.quantity, 0));

  add(product: Product, quantity = 1): void {
    const next = new Map(this.lineMap());
    const existing = next.get(product.id);
    next.set(product.id, { product, quantity: (existing?.quantity ?? 0) + quantity });
    this.lineMap.set(next);
  }

  updateQuantity(productId: string, quantity: number): void {
    const next = new Map(this.lineMap());
    const existing = next.get(productId);
    if (!existing) {
      return;
    }
    if (quantity <= 0) {
      next.delete(productId);
    } else {
      next.set(productId, { ...existing, quantity });
    }
    this.lineMap.set(next);
  }

  remove(productId: string): void {
    const next = new Map(this.lineMap());
    next.delete(productId);
    this.lineMap.set(next);
  }

  clear(): void {
    this.lineMap.set(new Map());
  }

  toPlaceOrderInput(): PlaceOrderInput {
    return { items: this.lines().map((line) => ({ productId: line.product.id, quantity: line.quantity })) };
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/cart.service.spec.ts'`
Expected: PASS (6 specs).

- [ ] **Step 5: Commit**

```bash
git add src/app/features/orders/cart.service.ts src/app/features/orders/cart.service.spec.ts
git commit -m "feat(communication-protocols): add CartService to the GraphQL angular-demo"
```

---

### Task 18: OrderService

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/orders.gql.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/order.service.ts`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/orders/order.service.spec.ts`

**Interfaces:**
- Consumes: `Connection`, `Order`, `OrderStatus`, `PlaceOrderInput` (Task 2), apollo-angular's `Apollo`.
- Produces: `OrderService` (`providedIn: 'root'`) with `listMyOrders(first: number, after: string | null): Observable<Connection<Order>>`, `listAllOrders(status: OrderStatus | null, first: number, after: string | null): Observable<Connection<Order>>`, `getOrder(id: string): Observable<Order | null>`, `placeOrder(input: PlaceOrderInput): Observable<Order>`, `updateOrderStatus(id: string, status: OrderStatus): Observable<Order>`. Consumed by `OrderList`/`PlaceOrder` (Task 19) and `OrderDetail` (Task 20).

- [ ] **Step 1: Write the GraphQL documents**

`src/app/features/orders/orders.gql.ts`:

```ts
import { gql } from 'apollo-angular';

export const MY_ORDERS_QUERY = gql`
  query MyOrders($first: Int, $after: String) {
    me {
      id
      orders(first: $first, after: $after) {
        totalCount
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          cursor
          node {
            id
            status
            placedAt
            totalCents
          }
        }
      }
    }
  }
`;

export const ALL_ORDERS_QUERY = gql`
  query AllOrders($status: OrderStatus, $first: Int, $after: String) {
    orders(status: $status, first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        cursor
        node {
          id
          status
          placedAt
          totalCents
          user {
            id
            displayName
          }
        }
      }
    }
  }
`;

export const ORDER_QUERY = gql`
  query Order($id: ID!) {
    order(id: $id) {
      id
      status
      placedAt
      totalCents
      user {
        id
        displayName
      }
      items {
        id
        quantity
        unitPriceCents
        lineTotalCents
        product {
          id
          name
        }
      }
    }
  }
`;

export const PLACE_ORDER_MUTATION = gql`
  mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
      id
      status
      placedAt
      totalCents
    }
  }
`;

export const UPDATE_ORDER_STATUS_MUTATION = gql`
  mutation UpdateOrderStatus($id: ID!, $status: OrderStatus!) {
    updateOrderStatus(id: $id, status: $status) {
      id
      status
    }
  }
`;
```

- [ ] **Step 2: Write the failing test**

`src/app/features/orders/order.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Apollo } from 'apollo-angular';
import { of } from 'rxjs';
import { OrderService } from './order.service';
import { Connection, Order, emptyConnection } from '../../core/graphql/graphql.models';

describe('OrderService', () => {
  let apollo: jasmine.SpyObj<Apollo>;
  let service: OrderService;

  beforeEach(() => {
    apollo = jasmine.createSpyObj<Apollo>(['watchQuery', 'mutate']);
    TestBed.configureTestingModule({ providers: [{ provide: Apollo, useValue: apollo }] });
    service = TestBed.inject(OrderService);
  });

  it('listMyOrders maps me.orders', (done) => {
    const connection: Connection<Order> = { ...emptyConnection<Order>(), totalCount: 2 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { me: { orders: connection } } }) } as never);

    service.listMyOrders(20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('listAllOrders maps the orders connection', (done) => {
    const connection: Connection<Order> = { ...emptyConnection<Order>(), totalCount: 4 };
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { orders: connection } }) } as never);

    service.listAllOrders('PAID', 20, null).subscribe((result) => {
      expect(result).toEqual(connection);
      done();
    });
  });

  it('getOrder maps a single order', (done) => {
    const order = { id: '1' } as Order;
    apollo.watchQuery.and.returnValue({ valueChanges: of({ data: { order } }) } as never);

    service.getOrder('1').subscribe((result) => {
      expect(result).toEqual(order);
      done();
    });
  });

  it('placeOrder maps the created order', (done) => {
    const order = { id: '2' } as Order;
    apollo.mutate.and.returnValue(of({ data: { placeOrder: order } }) as never);

    service.placeOrder({ items: [{ productId: '1', quantity: 1 }] }).subscribe((result) => {
      expect(result).toEqual(order);
      done();
    });
  });

  it('updateOrderStatus maps the updated order', (done) => {
    const order = { id: '2', status: 'SHIPPED' } as Order;
    apollo.mutate.and.returnValue(of({ data: { updateOrderStatus: order } }) as never);

    service.updateOrderStatus('2', 'SHIPPED').subscribe((result) => {
      expect(result).toEqual(order);
      done();
    });
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/order.service.spec.ts'`
Expected: FAIL — `./order.service` module not found.

- [ ] **Step 4: Implement OrderService**

`src/app/features/orders/order.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { Connection, Order, OrderStatus, PlaceOrderInput } from '../../core/graphql/graphql.models';
import { ALL_ORDERS_QUERY, MY_ORDERS_QUERY, ORDER_QUERY, PLACE_ORDER_MUTATION, UPDATE_ORDER_STATUS_MUTATION } from './orders.gql';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly apollo = inject(Apollo);

  listMyOrders(first: number, after: string | null): Observable<Connection<Order>> {
    return this.apollo
      .watchQuery<{ me: { orders: Connection<Order> } }>({
        query: MY_ORDERS_QUERY,
        variables: { first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.me.orders));
  }

  listAllOrders(status: OrderStatus | null, first: number, after: string | null): Observable<Connection<Order>> {
    return this.apollo
      .watchQuery<{ orders: Connection<Order> }>({
        query: ALL_ORDERS_QUERY,
        variables: { status, first, after },
        fetchPolicy: 'network-only',
      })
      .valueChanges.pipe(map((result) => result.data.orders));
  }

  getOrder(id: string): Observable<Order | null> {
    return this.apollo
      .watchQuery<{ order: Order | null }>({ query: ORDER_QUERY, variables: { id }, fetchPolicy: 'network-only' })
      .valueChanges.pipe(map((result) => result.data.order));
  }

  placeOrder(input: PlaceOrderInput): Observable<Order> {
    return this.apollo
      .mutate<{ placeOrder: Order }>({ mutation: PLACE_ORDER_MUTATION, variables: { input } })
      .pipe(map((result) => result.data!.placeOrder));
  }

  updateOrderStatus(id: string, status: OrderStatus): Observable<Order> {
    return this.apollo
      .mutate<{ updateOrderStatus: Order }>({ mutation: UPDATE_ORDER_STATUS_MUTATION, variables: { id, status } })
      .pipe(map((result) => result.data!.updateOrderStatus));
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/order.service.spec.ts'`
Expected: PASS (5 specs).

- [ ] **Step 6: Commit**

```bash
git add src/app/features/orders/orders.gql.ts src/app/features/orders/order.service.ts \
  src/app/features/orders/order.service.spec.ts
git commit -m "feat(communication-protocols): add OrderService to the GraphQL angular-demo"
```

---

### Task 19: OrderList + PlaceOrder + orders routes

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/place-order.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/place-order.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/orders/place-order.spec.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/order-list.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/order-list.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/orders/order-list.spec.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/orders.routes.ts`

**Interfaces:**
- Consumes: `OrderService` (Task 18), `CartService` (Task 17), `AuthService` (Task 3), `ConnectionPaginator` (Task 9).
- Produces: `PlaceOrder` component (`app-place-order`, no inputs — reads `CartService` directly); `OrderList` component (`app-order-list`), routed at `orders` (root of `ORDERS_ROUTES`). `ORDERS_ROUTES`.

- [ ] **Step 1: Write the failing test for PlaceOrder**

`src/app/features/orders/place-order.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PlaceOrder } from './place-order';
import { OrderService } from './order.service';
import { CartService } from './cart.service';
import { Order, Product } from '../../core/graphql/graphql.models';

describe('PlaceOrder', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let cartService: CartService;
  const widget: Product = { id: '1', name: 'Widget', priceCents: 500, stockQty: 10 };

  beforeEach(() => {
    orderService = jasmine.createSpyObj<OrderService>(['placeOrder']);
    TestBed.configureTestingModule({
      imports: [PlaceOrder],
      providers: [{ provide: OrderService, useValue: orderService }],
    });
    cartService = TestBed.inject(CartService);
  });

  it('shows the current cart lines and total', () => {
    cartService.add(widget, 2);

    const fixture = TestBed.createComponent(PlaceOrder);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Widget');
    expect(fixture.componentInstance.cartService.totalCents()).toBe(1000);
  });

  it('placing the order submits the cart and clears it on success', () => {
    cartService.add(widget, 2);
    const placedOrder = { id: '5' } as Order;
    orderService.placeOrder.and.returnValue(of(placedOrder));

    const fixture = TestBed.createComponent(PlaceOrder);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    expect(orderService.placeOrder).toHaveBeenCalledWith({ items: [{ productId: '1', quantity: 2 }] });
    expect(cartService.lines()).toEqual([]);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/place-order.spec.ts'`
Expected: FAIL — `./place-order` module not found.

- [ ] **Step 3: Implement PlaceOrder**

`src/app/features/orders/place-order.ts`:

```ts
import { Component, inject, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { CartService } from './cart.service';
import { OrderService } from './order.service';

@Component({
  selector: 'app-place-order',
  imports: [DecimalPipe, MatButtonModule, MatListModule],
  templateUrl: './place-order.html',
})
export class PlaceOrder {
  readonly cartService = inject(CartService);
  private readonly orderService = inject(OrderService);

  readonly ordered = output<void>();

  submit(): void {
    if (this.cartService.lines().length === 0) {
      return;
    }
    this.orderService.placeOrder(this.cartService.toPlaceOrderInput()).subscribe(() => {
      this.cartService.clear();
      this.ordered.emit();
    });
  }
}
```

`src/app/features/orders/place-order.html`:

```html
<mat-nav-list>
  @for (line of cartService.lines(); track line.product.id) {
    <mat-list-item>{{ line.product.name }} × {{ line.quantity }}</mat-list-item>
  }
</mat-nav-list>
<p>Total: {{ cartService.totalCents() / 100 | number: '1.2-2' }}</p>
<button
  mat-raised-button
  color="primary"
  [disabled]="cartService.lines().length === 0"
  (click)="submit()"
  data-testid="place-order-button"
>
  Place order
</button>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/place-order.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 5: Write the failing test for OrderList**

`src/app/features/orders/order-list.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { RouterModule, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OrderList } from './order-list';
import { OrderService } from './order.service';
import { AuthService } from '../../core/auth/auth.service';
import { Connection, Order, emptyConnection } from '../../core/graphql/graphql.models';

describe('OrderList', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let authService: AuthService;

  const myOrders: Connection<Order> = { ...emptyConnection<Order>(), edges: [{ cursor: 'o1', node: { id: '1' } as Order }], totalCount: 1 };
  const allOrders: Connection<Order> = { ...emptyConnection<Order>(), edges: [{ cursor: 'o2', node: { id: '2' } as Order }], totalCount: 1 };

  beforeEach(() => {
    sessionStorage.clear();
    orderService = jasmine.createSpyObj<OrderService>(['listMyOrders', 'listAllOrders']);
    orderService.listMyOrders.and.returnValue(of(myOrders));
    orderService.listAllOrders.and.returnValue(of(allOrders));
    TestBed.configureTestingModule({
      imports: [OrderList, RouterModule],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: OrderService, useValue: orderService }],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads only "my orders" for a CUSTOMER', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '1', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(OrderList);
    fixture.detectChanges();

    expect(orderService.listMyOrders).toHaveBeenCalledWith(20, null);
    expect(orderService.listAllOrders).not.toHaveBeenCalled();
    expect(fixture.componentInstance.myOrderEdges().length).toBe(1);
  });

  it('also loads "all orders" for an ADMIN', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });

    const fixture = TestBed.createComponent(OrderList);
    fixture.detectChanges();

    expect(orderService.listMyOrders).toHaveBeenCalledWith(20, null);
    expect(orderService.listAllOrders).toHaveBeenCalledWith(null, 20, null);
    expect(fixture.componentInstance.allOrderEdges().length).toBe(1);
  });
});
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/order-list.spec.ts'`
Expected: FAIL — `./order-list` module not found.

- [ ] **Step 7: Implement OrderList**

`src/app/features/orders/order-list.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { ConnectionPaginator } from '../../shared/connection-paginator/connection-paginator';
import { AuthService } from '../../core/auth/auth.service';
import { Edge, Order, OrderStatus, PageInfo, emptyConnection } from '../../core/graphql/graphql.models';
import { OrderService } from './order.service';
import { PlaceOrder } from './place-order';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-order-list',
  imports: [RouterLink, MatTabsModule, MatSelectModule, MatListModule, ConnectionPaginator, PlaceOrder],
  templateUrl: './order-list.html',
})
export class OrderList {
  private readonly orderService = inject(OrderService);
  protected readonly authService = inject(AuthService);

  readonly myOrderEdges = signal<Edge<Order>[]>([]);
  readonly myOrderPageInfo = signal<PageInfo>(emptyConnection<Order>().pageInfo);
  readonly myOrderTotalCount = signal(0);

  readonly allOrderEdges = signal<Edge<Order>[]>([]);
  readonly allOrderPageInfo = signal<PageInfo>(emptyConnection<Order>().pageInfo);
  readonly allOrderTotalCount = signal(0);
  readonly statusFilter = signal<OrderStatus | null>(null);

  constructor() {
    this.loadMyOrders(null);
    if (this.authService.currentUser()?.role === 'ADMIN') {
      this.loadAllOrders(null);
    }
  }

  loadMoreMyOrders(): void {
    this.loadMyOrders(this.myOrderPageInfo().endCursor);
  }

  loadMoreAllOrders(): void {
    this.loadAllOrders(this.allOrderPageInfo().endCursor);
  }

  refreshMyOrders(): void {
    this.myOrderEdges.set([]);
    this.loadMyOrders(null);
  }

  filterAllOrdersByStatus(status: OrderStatus | null): void {
    this.statusFilter.set(status);
    this.allOrderEdges.set([]);
    this.loadAllOrders(null);
  }

  private loadMyOrders(after: string | null): void {
    this.orderService.listMyOrders(PAGE_SIZE, after).subscribe((connection) => {
      this.myOrderEdges.set([...this.myOrderEdges(), ...connection.edges]);
      this.myOrderPageInfo.set(connection.pageInfo);
      this.myOrderTotalCount.set(connection.totalCount);
    });
  }

  private loadAllOrders(after: string | null): void {
    this.orderService.listAllOrders(this.statusFilter(), PAGE_SIZE, after).subscribe((connection) => {
      this.allOrderEdges.set([...this.allOrderEdges(), ...connection.edges]);
      this.allOrderPageInfo.set(connection.pageInfo);
      this.allOrderTotalCount.set(connection.totalCount);
    });
  }
}
```

`src/app/features/orders/order-list.html`:

```html
<app-place-order (ordered)="refreshMyOrders()" />

@if (authService.currentUser()?.role === 'ADMIN') {
  <mat-tab-group>
    <mat-tab label="My Orders">
      <ng-template matTabContent>
        <mat-nav-list>
          @for (edge of myOrderEdges(); track edge.node.id) {
            <a mat-list-item [routerLink]="[edge.node.id]">{{ edge.node.id }} — {{ edge.node.status }}</a>
          }
        </mat-nav-list>
        <app-connection-paginator
          [hasNextPage]="myOrderPageInfo().hasNextPage"
          [totalCount]="myOrderTotalCount()"
          [loadedCount]="myOrderEdges().length"
          (loadMore)="loadMoreMyOrders()"
        />
      </ng-template>
    </mat-tab>
    <mat-tab label="All Orders">
      <ng-template matTabContent>
        <mat-select [value]="statusFilter()" (selectionChange)="filterAllOrdersByStatus($event.value)" data-testid="status-filter">
          <mat-option [value]="null">Any status</mat-option>
          @for (status of ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED']; track status) {
            <mat-option [value]="status">{{ status }}</mat-option>
          }
        </mat-select>
        <mat-nav-list>
          @for (edge of allOrderEdges(); track edge.node.id) {
            <a mat-list-item [routerLink]="[edge.node.id]">{{ edge.node.id }} — {{ edge.node.user.displayName }} — {{ edge.node.status }}</a>
          }
        </mat-nav-list>
        <app-connection-paginator
          [hasNextPage]="allOrderPageInfo().hasNextPage"
          [totalCount]="allOrderTotalCount()"
          [loadedCount]="allOrderEdges().length"
          (loadMore)="loadMoreAllOrders()"
        />
      </ng-template>
    </mat-tab>
  </mat-tab-group>
} @else {
  <mat-nav-list>
    @for (edge of myOrderEdges(); track edge.node.id) {
      <a mat-list-item [routerLink]="[edge.node.id]">{{ edge.node.id }} — {{ edge.node.status }}</a>
    }
  </mat-nav-list>
  <app-connection-paginator
    [hasNextPage]="myOrderPageInfo().hasNextPage"
    [totalCount]="myOrderTotalCount()"
    [loadedCount]="myOrderEdges().length"
    (loadMore)="loadMoreMyOrders()"
  />
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/order-list.spec.ts'`
Expected: PASS (2 specs).

- [ ] **Step 9: Add orders routes**

`src/app/features/orders/orders.routes.ts`:

```ts
import { Routes } from '@angular/router';

export const ORDERS_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./order-list').then((m) => m.OrderList) },
  { path: ':id', loadComponent: () => import('./order-detail').then((m) => m.OrderDetail) },
];
```

This references `./order-detail`, which doesn't exist until Task 20 — that's expected; `npx tsc -p tsconfig.app.json --noEmit` will still fail until then.

- [ ] **Step 10: Commit**

```bash
git add src/app/features/orders/place-order.ts src/app/features/orders/place-order.html \
  src/app/features/orders/place-order.spec.ts src/app/features/orders/order-list.ts \
  src/app/features/orders/order-list.html src/app/features/orders/order-list.spec.ts \
  src/app/features/orders/orders.routes.ts
git commit -m "feat(communication-protocols): add order list and place-order to the GraphQL angular-demo"
```

---

### Task 20: OrderDetail

**Files:**
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/order-detail.ts`
- Create: `communication-protocols/graphql/angular-demo/src/app/features/orders/order-detail.html`
- Test: `communication-protocols/graphql/angular-demo/src/app/features/orders/order-detail.spec.ts`

**Interfaces:**
- Consumes: `OrderService` (Task 18), `AuthService` (Task 3).
- Produces: `OrderDetail` component, routed at `orders/:id` (Task 19's `ORDERS_ROUTES`).

- [ ] **Step 1: Write the failing test**

`src/app/features/orders/order-detail.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OrderDetail } from './order-detail';
import { OrderService } from './order.service';
import { AuthService } from '../../core/auth/auth.service';
import { Order } from '../../core/graphql/graphql.models';

describe('OrderDetail', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let authService: AuthService;

  const order: Order = {
    id: '1',
    user: { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' },
    status: 'PENDING',
    placedAt: '2026-08-01T00:00:00Z',
    items: [{ id: '1', product: { id: '9', name: 'Widget', priceCents: 500, stockQty: 1 }, quantity: 2, unitPriceCents: 500, lineTotalCents: 1000 }],
    totalCents: 1000,
  };

  beforeEach(() => {
    sessionStorage.clear();
    orderService = jasmine.createSpyObj<OrderService>(['getOrder', 'updateOrderStatus']);
    orderService.getOrder.and.returnValue(of(order));
    TestBed.configureTestingModule({
      imports: [OrderDetail],
      providers: [
        provideNoopAnimations(),
        { provide: OrderService, useValue: orderService },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
      ],
    });
    authService = TestBed.inject(AuthService);
  });

  it('loads the order for the route id', () => {
    const fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();

    expect(orderService.getOrder).toHaveBeenCalledWith('1');
    expect(fixture.componentInstance.order()).toEqual(order);
  });

  it('hides the status-update control for a non-admin user', () => {
    authService.setSession({ username: 'user', password: 'userPassword' }, { id: '2', username: 'user', displayName: 'Demo User', role: 'CUSTOMER' });

    const fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="status-select"]')).toBeNull();
  });

  it('shows and wires the status-update control for an admin user', () => {
    authService.setSession({ username: 'admin', password: 'adminPassword' }, { id: '3', username: 'admin', displayName: 'Demo Admin', role: 'ADMIN' });
    orderService.updateOrderStatus.and.returnValue(of({ ...order, status: 'SHIPPED' }));

    const fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();
    fixture.componentInstance.updateStatus('SHIPPED');

    expect(orderService.updateOrderStatus).toHaveBeenCalledWith('1', 'SHIPPED');
    expect(fixture.componentInstance.order()?.status).toBe('SHIPPED');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/order-detail.spec.ts'`
Expected: FAIL — `./order-detail` module not found.

- [ ] **Step 3: Implement OrderDetail**

`src/app/features/orders/order-detail.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { AuthService } from '../../core/auth/auth.service';
import { Order, OrderStatus } from '../../core/graphql/graphql.models';
import { OrderService } from './order.service';

const ORDER_STATUSES: OrderStatus[] = ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

@Component({
  selector: 'app-order-detail',
  imports: [DecimalPipe, MatSelectModule, MatListModule],
  templateUrl: './order-detail.html',
})
export class OrderDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);
  protected readonly authService = inject(AuthService);
  protected readonly statuses = ORDER_STATUSES;

  private readonly orderId = this.route.snapshot.paramMap.get('id')!;

  readonly order = signal<Order | null>(null);

  constructor() {
    this.orderService.getOrder(this.orderId).subscribe((order) => this.order.set(order));
  }

  updateStatus(status: OrderStatus): void {
    this.orderService.updateOrderStatus(this.orderId, status).subscribe((order) => this.order.set(order));
  }
}
```

`src/app/features/orders/order-detail.html`:

```html
@if (order(); as o) {
  <h2>Order {{ o.id }} — {{ o.status }}</h2>
  <p>Placed by {{ o.user.displayName }} on {{ o.placedAt }}</p>

  <mat-nav-list>
    @for (item of o.items; track item.id) {
      <mat-list-item>
        {{ item.product.name }} × {{ item.quantity }} — {{ item.lineTotalCents / 100 | number: '1.2-2' }}
      </mat-list-item>
    }
  </mat-nav-list>
  <p>Total: {{ o.totalCents / 100 | number: '1.2-2' }}</p>

  @if (authService.currentUser()?.role === 'ADMIN') {
    <mat-select [value]="o.status" (selectionChange)="updateStatus($event.value)" data-testid="status-select">
      @for (status of statuses; track status) {
        <mat-option [value]="status">{{ status }}</mat-option>
      }
    </mat-select>
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watch=false --browsers=ChromeHeadless --include='**/order-detail.spec.ts'`
Expected: PASS (3 specs).

- [ ] **Step 5: Verify the whole app now compiles**

Run: `npx tsc -p tsconfig.app.json --noEmit`
Expected: no errors — every route target referenced from `app.routes.ts`, `catalog.routes.ts`, and `orders.routes.ts` now exists.

- [ ] **Step 6: Commit**

```bash
git add src/app/features/orders/order-detail.ts src/app/features/orders/order-detail.html \
  src/app/features/orders/order-detail.spec.ts
git commit -m "feat(communication-protocols): add order detail with admin status updates to the GraphQL angular-demo"
```

---

### Task 21: Full verification, README, and CLAUDE.md

**Files:**
- Create: `communication-protocols/graphql/angular-demo/README.md`
- Modify: `communication-protocols/CLAUDE.md` root `/CLAUDE.md` (commands + architecture table)

**Interfaces:**
- Consumes: nothing new — this task only documents and verifies what Tasks 1-20 built.

- [ ] **Step 1: Run the full unit test suite**

```bash
cd communication-protocols/graphql/angular-demo
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: every spec file from Tasks 3-20 passes (no regressions from later tasks touching shared files like `AuthService`/`graphql.models.ts`).

- [ ] **Step 2: Run a production build**

```bash
npm run build
```

Expected: succeeds with no TypeScript errors.

- [ ] **Step 3: Manual smoke test against the running Spring app**

```bash
docker compose -f ../docker/docker-compose.yml up -d
cd ../spring-demo && mvn spring-boot:run &
cd ../angular-demo && npm start
```

Open `http://localhost:4202`, log in as `admin`/`adminPassword` via the quick-select button, and confirm: the catalog loads and filters, a product detail page shows nested reviews and lets you add one, `/categories` expands a node's children and products, `/live` receives a toast when you add a review to a product in another tab, and `/orders` shows both tabs with a working "All Orders" status filter. Stop both processes afterward.

- [ ] **Step 4: Write the module README**

`communication-protocols/graphql/angular-demo/README.md`:

```markdown
# GraphQL Angular Demo

A standalone Angular 21 app that exercises every GraphQL pattern demonstrated by [`../spring-demo`](../spring-demo): query + nested fetch, DataLoader batching, mutation, subscription, and pagination & filtering — plus the role-based and row-level authorization built into that app's schema.

## Running

Requires `graphql/spring-demo` running first (see [`../README.md`](../README.md)):

\`\`\`bash
docker compose -f ../docker/docker-compose.yml up -d
cd ../spring-demo && mvn spring-boot:run
\`\`\`

Then, in this directory:

\`\`\`bash
npm install
npm start   # dev server on :4202, proxies /graphql (HTTP + WS) to :8092
\`\`\`

Open http://localhost:4202 and log in with the "Continue as user" or "Continue as admin" quick-select button (credentials match `graphql/spring-demo`'s seeded accounts).

## Why a dev proxy

The Spring app has no CORS configuration. `proxy.conf.json` makes `ng serve` proxy `/graphql` (both HTTP and the WebSocket upgrade) to `http://localhost:8092`, so the browser sees everything as same-origin — no backend changes needed. This is dev-only; there's no production deployment story here (same scope limit as the rest of `communication-protocols`).

## Why login establishes a session, not just a header

Every query/mutation carries an `Authorization: Basic` header (added by an `HttpInterceptor`). Browsers can't attach that header to a WebSocket handshake, though, so the `reviewAdded` subscription can't authenticate that way. Instead, login's one HTTP-Basic-authenticated request (`me`) causes Spring Security to persist the authentication to an `HttpSession` and set a `JSESSIONID` cookie (`SecurityConfig` never sets `SessionCreationPolicy.STATELESS`). Because the WebSocket connects through the same proxied origin, the browser attaches that cookie to the WS handshake automatically, authenticating the subscription. This is why the whole app sits behind `/login`, even though `products`/`categories` don't themselves require authentication.

## Feature tour

| Route | Pattern(s) |
|---|---|
| `/login` | HTTP Basic auth, session establishment |
| `/catalog` | `products(filter, first, after)` |
| `/catalog/:id` | nested `reviews`, `addReview`, `deleteReview` (ADMIN) |
| `/categories` | `categories` → `children` (DataLoader-batched), `category.products` |
| `/live` | `reviewAdded(productId?)` subscription |
| `/orders` | `me.orders`, `orders(status)` (ADMIN), `placeOrder` |
| `/orders/:id` | `order(id)` (owner-or-admin), `updateOrderStatus` (ADMIN) |

## Testing

\`\`\`bash
npm test       # Karma/Jasmine unit tests — no e2e layer
npm run build  # production build
\`\`\`
```

- [ ] **Step 5: Update the root CLAUDE.md**

In `/CLAUDE.md`, add a new commands subsection after the existing "### WebSocket communication protocol demo" block (keep the same style/heading level):

````markdown
### GraphQL Angular demo (run from the app root, requires `graphql/spring-demo` running first)

```bash
cd communication-protocols/graphql/angular-demo

npm install
npm start              # dev server on :4202, proxies /graphql (HTTP + WS) to :8092
npm test                # Jasmine/Karma unit tests
npm run build           # production build
```
````

In the "Repository layout" table, add a row directly after the `communication-protocols/graphql/spring-demo/` row:

```markdown
| `communication-protocols/graphql/angular-demo/` | Angular 21 client for the GraphQL demo — Apollo Angular over a dev-proxy to `spring-demo`, touring all five patterns with role/row-level authorization; `npm start` requires `spring-demo` running first |
```

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/angular-demo/README.md CLAUDE.md
git commit -m "docs(communication-protocols): document the GraphQL angular-demo"
```
