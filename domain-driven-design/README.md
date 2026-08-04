# Domain-Driven Design

Demo modules for tactical Domain-Driven Design patterns.

## banking/spring-demo

A banking ledger demonstrating:

- **Entities & aggregates** — `Account` enforces its own invariants (no negative balances, no currency mixing) rather than leaving validation to a service layer.
- **Value objects** — `AccountId`, `Money`.
- **Domain events** — `AccountOpened`, `MoneyDeposited`, `MoneyWithdrawn`, a sealed hierarchy.
- **Repository as a port** — `AccountRepository` is a domain interface; `JpaAccountRepositoryAdapter` is the only class that knows about JPA.
- **Domain service** — `TransferService` coordinates two aggregates for a transfer, which no single `Account` can do on its own.
- **Bounded contexts + anti-corruption layer** — a second context, `statements`, maintains its own read model (`StatementLine`) built exclusively by translating `ledger`'s domain events in `LedgerEventTranslator`, without ever depending on `ledger`'s domain types anywhere else.

```
ledger (domain core) --publishes--> LedgerEvent --consumed by--> statements.acl.LedgerEventTranslator --writes--> statements (its own read model)
```

No Docker required — the app runs entirely on an in-memory H2 database. See `banking/spring-demo/README.md` for how to run it.
