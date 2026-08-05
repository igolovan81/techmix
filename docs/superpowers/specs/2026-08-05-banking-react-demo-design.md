# Banking React Demo Design

**Date:** 2026-08-05
**Status:** Approved

## Overview

A new standalone React application at `domain-driven-design/banking/react-demo/`, sibling to `banking/spring-demo`. It is a browser client exercising the full ledger workflow against the existing REST API: open an account, deposit, withdraw, transfer between accounts, and view an account's statement.

## Goals

- Give every existing REST endpoint a working UI: `POST /accounts`, `GET /accounts/{id}`, `POST /accounts/{id}/deposits`, `POST /accounts/{id}/withdrawals`, `POST /transfers`, `GET /accounts/{accountId}/statement`.
- Use a current, idiomatic React stack (Vite + React + TypeScript + TanStack Query) without over-engineering a demo.
- Connect to the running Spring app with zero changes to `banking/spring-demo`.

## Non-goals

- No backend changes, including no new "list all accounts" endpoint (see constraint below).
- No routing library — three tabs (Accounts / Transfer / Statement) don't need one.
- No e2e test layer (Playwright/Gatling) — unit/component tests only, matching this repo's "unit tests, no e2e" convention for backend-paired demo UIs.
- No CSS framework — plain CSS, dependency-light.
- No SSR — Next.js was considered and rejected as unnecessary weight for a demo hitting a fixed backend port.

## Known constraint: no list-accounts endpoint

`AccountController` only supports open / get-by-id / deposit / withdraw; there is no endpoint to list all accounts. Rather than add one to the backend (out of scope for a UI-only demo), the app keeps a small client-side **registry** of account IDs it has created or looked up, persisted to `localStorage`. This is how the "Accounts" tab populates its list — it reflects what this browser has seen, not a true backend listing. Any account ID can still be looked up directly (e.g. pasted into the Transfer or Statement tabs) even if it's not in the local registry.

## Connecting to the Spring app

**CORS.** The Spring app has no CORS configuration, so cross-origin requests from `:4203` would be blocked by the browser. Vite's dev server proxies API paths to `http://localhost:8099`:

```ts
// vite.config.ts
server: {
  port: 4203,
  proxy: {
    '/accounts': 'http://localhost:8099',
    '/transfers': 'http://localhost:8099',
  },
}
```

The browser sees everything as same-origin `:4203`; no backend CORS bean needed.

No authentication exists on this backend, so there is no session/auth story to solve (unlike the GraphQL Angular demo).

## Repository structure

```
domain-driven-design/banking/
├── spring-demo/                          (existing, untouched)
└── react-demo/
    ├── package.json                      (React ^19, TypeScript, Vite, @tanstack/react-query; "dev" script uses --port 4203)
    ├── vite.config.ts                    (dev proxy config above)
    ├── vitest.config.ts
    ├── README.md
    └── src/
        ├── main.tsx                      (QueryClientProvider + App)
        ├── App.tsx                       (tab shell: Accounts | Transfer | Statement)
        ├── App.css
        ├── api/
        │   ├── types.ts                  (OpenAccountRequest/Response, AmountRequest, AccountResponse, TransferRequest/Response, StatementLine — hand-mirrored from the Java DTOs, no codegen)
        │   ├── client.ts                 (typed fetch wrapper; parses {error, message} bodies from DomainExceptionHandler into a thrown ApiError)
        │   └── queries.ts                (TanStack Query hooks: useOpenAccount, useAccount(id), useDeposit, useWithdraw, useTransfer, useStatement(accountId))
        ├── accounts/
        │   ├── AccountRegistry.ts         (localStorage-backed known-account-IDs store + hook)
        │   ├── AccountRegistry.test.ts
        │   ├── OpenAccountForm.tsx
        │   ├── OpenAccountForm.test.tsx
        │   ├── AccountCard.tsx            (balance display + deposit/withdraw inline forms)
        │   ├── AccountCard.test.tsx
        │   └── AccountsTab.tsx            (list of registered accounts + OpenAccountForm)
        ├── transfer/
        │   ├── TransferForm.tsx
        │   └── TransferForm.test.tsx
        └── statement/
            ├── StatementView.tsx
            └── StatementView.test.tsx
```

## Feature tour (tabs)

| Tab | Endpoint(s) | Notes |
|---|---|---|
| Accounts | `POST /accounts`, `GET /accounts/{id}`, `POST /accounts/{id}/deposits`, `POST /accounts/{id}/withdrawals` | Opening an account adds its ID to the local registry; deposit/withdraw refetch the account via TanStack Query cache invalidation |
| Transfer | `POST /transfers` | Source/target account IDs picked from the registry (dropdown) or typed directly |
| Statement | `GET /accounts/{accountId}/statement` | Account ID picked from the registry or typed directly; renders lines with type (CREDIT/DEBIT), amount, description, timestamp |

## Error handling

`api/client.ts` parses the JSON error body Spring returns (`{"error": "...", "message": "..."}`, from `DomainExceptionHandler` — `ACCOUNT_NOT_FOUND` on 404, exception simple name on 400) and throws an `ApiError { code, message }`. Each form (`OpenAccountForm`, `AccountCard`'s deposit/withdraw, `TransferForm`) shows that message inline near its submit button on failure via TanStack Query's mutation `error` state — no global toast/error boundary, matching the demo's minimal scope.

## Testing strategy

Vitest + React Testing Library, no e2e:

- `AccountRegistry` — add/list/persist round-trip against a mocked `localStorage`.
- `api/client` — response parsing and `ApiError` construction for both success and `{error, message}` failure bodies (mocked `fetch`).
- `OpenAccountForm`, `AccountCard`, `TransferForm`, `StatementView` — form submission calls the right mutation/query hook with the right arguments and renders returned/error state (TanStack Query client + mocked `fetch`, no live server).

## Commands (added to CLAUDE.md)

```bash
cd domain-driven-design/banking/react-demo

npm install
npm run dev             # dev server on :4203, proxies /accounts and /transfers to :8099
npm test                 # Vitest unit/component tests
npm run build            # production build
```

Requires `banking/spring-demo` running first (`mvn -pl banking/spring-demo spring-boot:run`, per the existing `CLAUDE.md` entry).
