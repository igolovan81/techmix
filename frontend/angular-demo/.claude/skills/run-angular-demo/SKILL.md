---
name: run-angular-demo
description: Build, run, test, and screenshot the Angular capabilities demo (frontend/angular-demo). Use when asked to start angular-demo, run its dev server, take a screenshot of its UI, verify a change renders, or run its unit/e2e tests.
---

Self-contained Angular 21 app with no backend. Drive it via
`.claude/skills/run-angular-demo/driver.mjs` — it starts the dev
server, launches headless Chromium with `playwright-core` (already a
transitive dependency of the project's `@playwright/test` — no extra
install needed), exercises one representative interaction on a few key
pages, and screenshots each step.

All paths below are relative to `frontend/angular-demo/`.

## Prerequisites

None beyond `npm install` — no OS packages, no xvfb (headless Chromium
runs fine, no display needed). Node must satisfy `@angular/core`'s
engine range (`^20.19.0 || ^22.12.0 || >=24.0.0`); this container's
active Node (`v24.4.0`) already qualifies.

## Setup

```bash
npm install
```

## Run (agent path)

```bash
node .claude/skills/run-angular-demo/driver.mjs
```

This kills any stale `ng serve` from a previous run, starts a fresh
dev server on `:4201`, waits for it to answer, then:

1. Goes to `/` (redirects to `/signals`), screenshots.
2. Clicks "Increment" twice, waits for the count to read 2, screenshots
   — proves `signal`/`computed`/`effect` reactivity.
3. Navigates to Data Fetching, waits for the items list, screenshots —
   proves the real `HttpClient` GET against `public/data/items.json`
   round-trips.
4. Navigates to Animations, clicks "Add item", screenshots — proves
   the native `animate.enter` CSS binding fires.

It prints `CONSOLE_ERRORS: [...]` (should be `[]`) and exits 0 if empty,
1 otherwise. It always stops the dev server on the way out (including
on failure), so nothing lingers on `:4201`.

Screenshots → `.claude/skills/run-angular-demo/screenshots/*.png`
(gitignored, overwritten each run).

To check one specific route instead of the built-in flow, start the
server yourself and skip the driver's own navigation:

```bash
npm start &   # :4201
curl -s http://localhost:4201/data/items.json   # sanity-check the static asset
```

## Run (human path)

```bash
npm start   # dev server on http://localhost:4201, Ctrl-C to stop
```

## Test

```bash
npm test -- --watch=false --browsers=ChromeHeadless   # 22 Jasmine/Karma unit tests
npx playwright test                                    # 4 e2e tests, auto-starts the dev server
```

---

## Gotchas

- **This Angular 21 scaffold is zoneless (no `zone.js`).** If you write
  more Playwright/unit tests, don't reach for `fakeAsync`/`tick` — it
  throws `zone-testing.js is needed for the fakeAsync() test helper but
  could not be found`. Use real `async`/`await` timing instead.
- **`mat-sidenav-content` is the actual scroll container**, not the
  window. `page.mouse.wheel(...)` from a driver script won't trigger
  `@defer (on viewport)` on the Deferred Loading page — scroll that
  element directly: `page.locator('mat-sidenav-content').evaluate(el
  => el.scrollTo(0, el.scrollHeight))`.
- **`playwright-core` resolves fine from inside this skill directory**
  (`.claude/skills/run-angular-demo/driver.mjs`) because Node walks up
  parent directories for `node_modules` — but only if you run the
  script with `node`, not by piping it elsewhere. No separate install
  needed; it rides along with the project's `@playwright/test`
  devDependency.
- **`chromium-cli` was not available in this container** — that's why
  this skill has a real driver script instead of the usual heredoc. If
  `chromium-cli` is present in yours, it's a fine substitute; this
  driver still works either way.

## Troubleshooting

- **`EADDRINUSE` on `:4201`**: a previous `ng serve` is still running.
  `pkill -f "ng serve"` before retrying (the driver already does this
  automatically at the start of every run).
- **Driver hangs on `waitForServer`**: `ng serve` in dev mode compiles
  lazily; first boot after a cold `npm install` can take longer than
  the default 40s timeout. Re-run, or bump the timeout in
  `driver.mjs`.
