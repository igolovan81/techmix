# Angular Capabilities Demo

A self-contained Angular 21 application that tours the framework's main modern
capabilities. No backend, no Docker — everything runs from `npm install` +
`npm start`.

## Commands

```bash
npm install
npm start              # dev server on http://localhost:4201
npm test                # Jasmine/Karma unit tests
npx playwright test     # Playwright e2e tests (auto-starts the dev server)
npm run build           # production build
```

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
| `/animations` | native `animate.enter` / `animate.leave` CSS-driven transitions |

Each feature lives in its own folder under `src/app/features/<topic>/`,
self-contained with its own tests.
