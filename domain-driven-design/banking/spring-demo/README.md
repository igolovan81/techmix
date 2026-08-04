# DDD Banking Ledger Demo

Demonstrates tactical Domain-Driven Design patterns via a banking ledger: open an account, deposit, withdraw, transfer — with a second bounded context (`statements`) that maintains its own account-statement read model, fed only through an anti-corruption layer.

## Prerequisites

- Java 21
- Maven

No Docker needed — the app runs on an in-memory H2 database.

## Running

```bash
cd domain-driven-design
mvn -pl banking/spring-demo spring-boot:run
```

The app starts on `8099`. Swagger UI: http://localhost:8099/swagger-ui/index.html

## Patterns demonstrated

| Pattern | Class |
|---|---|
| Aggregate root with invariants | `ledger.domain.Account` |
| Value object | `ledger.domain.Money`, `ledger.domain.AccountId` |
| Domain event | `ledger.domain.event.LedgerEvent` (sealed) and its variants |
| Repository as a port | `ledger.domain.AccountRepository` (interface) / `ledger.infrastructure.JpaAccountRepositoryAdapter` (adapter) |
| Domain service across aggregates | `ledger.domain.TransferService` |
| Bounded context | `statements` — its own `StatementLine` model, never importing `ledger`'s types |
| Anti-corruption layer | `statements.infrastructure.acl.LedgerEventTranslator` |

## Walkthrough

Open two accounts:

```bash
curl -s -X POST localhost:8099/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","initialBalance":200.00,"currency":"USD"}'
# => {"accountId":"<alice-id>"}

curl -s -X POST localhost:8099/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","initialBalance":50.00,"currency":"USD"}'
# => {"accountId":"<bob-id>"}
```

Deposit into Alice's account:

```bash
curl -s -X POST localhost:8099/accounts/<alice-id>/deposits -H 'Content-Type: application/json' \
  -d '{"amount":100.00,"currency":"USD"}'
# => {"accountId":"<alice-id>","ownerName":"Alice","balance":300.0000,"currency":"USD"}
```

Try to withdraw more than the balance — rejected by the aggregate's own invariant, not a service-layer check:

```bash
curl -s -i -X POST localhost:8099/accounts/<alice-id>/withdrawals -H 'Content-Type: application/json' \
  -d '{"amount":10000.00,"currency":"USD"}'
# => HTTP/1.1 400
# => {"error":"InsufficientFundsException","message":"Account <alice-id> has insufficient funds for withdrawal of 10000.00"}
```

Transfer between the two accounts:

```bash
curl -s -X POST localhost:8099/transfers -H 'Content-Type: application/json' \
  -d '{"fromAccountId":"<alice-id>","toAccountId":"<bob-id>","amount":75.00,"currency":"USD"}'
# => {"transferId":"..."}
```

Check each account's statement — built entirely by the anti-corruption layer from `ledger`'s domain events, never read directly from `ledger`'s tables:

```bash
curl -s localhost:8099/accounts/<alice-id>/statement
curl -s localhost:8099/accounts/<bob-id>/statement
```

## Testing

```bash
mvn -pl banking/spring-demo test              # unit + integration tests (H2, Gatling excluded)
mvn -pl banking/spring-demo test -Dtest=AccountTest   # single test class
mvn -pl banking/spring-demo gatling:test      # load test — requires the app running first (see above)
```
