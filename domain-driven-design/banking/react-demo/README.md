# Banking React Demo

A React + TypeScript client for the [`banking/spring-demo`](../spring-demo) ledger REST API — open an account,
deposit, withdraw, transfer between accounts, and view an account's statement.

## Stack

Vite, React 19, TypeScript, TanStack Query for data fetching/caching, Vitest + React Testing Library for tests.
No router (three tabs, plain state), no CSS framework, no backend changes.

## Known accounts

The backend has no "list all accounts" endpoint. This app keeps a small registry of account IDs it has created
or looked up in the browser's `localStorage` — that's what populates the Accounts tab's list and the account
pickers on the Transfer/Statement tabs. Any account ID can still be pasted directly into those pickers even if
it isn't in the local registry.

## Running

Requires `banking/spring-demo` running first on port 8099:

```bash
cd ../spring-demo
mvn spring-boot:run
```

Then, in this directory:

```bash
npm install
npm run dev     # dev server on :4203, proxies /accounts and /transfers to :8099
npm test        # Vitest unit/component tests
npm run build   # production build
```
