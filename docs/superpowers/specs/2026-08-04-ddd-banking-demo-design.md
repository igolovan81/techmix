# DDD Banking Ledger Demo Design

**Date:** 2026-08-04
**Status:** Approved

## Overview

A new top-level `domain-driven-design/` category (sibling to `distributed-transactions/`, `cqrs-event-sourcing/`, `template-engines/`), containing a single Spring Boot demo app — `banking/spring-demo` — that demonstrates tactical Domain-Driven Design building blocks: entities, value objects, aggregates with enforced invariants, domain events, repositories-as-ports, a domain service coordinating two aggregates, and — the centerpiece — two bounded contexts connected through an anti-corruption layer (ACL).

The domain is a simple banking ledger: open an account, deposit, withdraw, transfer between accounts. `cqrs-event-sourcing/axon` already covers command/event/aggregate mechanics via an event-sourcing framework, so this module deliberately stays framework-free for the domain layer and instead showcases what that module doesn't: hexagonal architecture (domain has zero Spring/JPA dependencies), a second bounded context (`statements`) that never touches the first context's types, and the anti-corruption layer that translates between them.

No external infrastructure is required — H2 only, no docker-compose, matching `template-engines/` and `distributed-transactions/saga`. Domain events cross the ledger→statements boundary via Spring's synchronous `ApplicationEventPublisher`/`@EventListener`, not a message broker (that mechanic is already covered by `message-brokers/`).

## Repository structure

```
domain-driven-design/
├── pom.xml                                          (new parent POM, mirrors distributed-transactions/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview: tactical DDD patterns demonstrated, ledger/statements/ACL diagram)
└── banking/
    └── spring-demo/
        ├── pom.xml                                  (artifactId: banking-demo)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/banking/
            │   │   ├── BankingDemoApplication.java
            │   │   ├── ledger/
            │   │   │   ├── domain/
            │   │   │   │   ├── AggregateRoot.java              (base class: registerEvent, pullDomainEvents)
            │   │   │   │   ├── Account.java                    (aggregate root: open/deposit/withdraw, enforces invariants)
            │   │   │   │   ├── AccountId.java                  (record wrapping UUID)
            │   │   │   │   ├── Money.java                      (record: BigDecimal amount, Currency currency — plus/minus reject mismatched currency)
            │   │   │   │   ├── AccountRepository.java          (port interface)
            │   │   │   │   ├── TransferService.java            (domain service: withdraw source + deposit target, shared transferId)
            │   │   │   │   ├── event/
            │   │   │   │   │   ├── LedgerEvent.java             (sealed interface)
            │   │   │   │   │   ├── AccountOpened.java           (record)
            │   │   │   │   │   ├── MoneyDeposited.java          (record)
            │   │   │   │   │   └── MoneyWithdrawn.java          (record)
            │   │   │   │   └── exception/
            │   │   │   │       ├── DomainException.java         (base RuntimeException)
            │   │   │   │       ├── InsufficientFundsException.java
            │   │   │   │       ├── InvalidAmountException.java
            │   │   │   │       ├── CurrencyMismatchException.java
            │   │   │   │       └── AccountNotFoundException.java
            │   │   │   ├── application/
            │   │   │   │   ├── OpenAccountUseCase.java
            │   │   │   │   ├── DepositUseCase.java
            │   │   │   │   ├── WithdrawUseCase.java
            │   │   │   │   └── TransferMoneyUseCase.java        (each: load -> invoke domain behavior -> save -> publish pulled events)
            │   │   │   ├── infrastructure/
            │   │   │   │   ├── AccountJpaEntity.java            (only JPA-annotated class in this context)
            │   │   │   │   ├── AccountMapper.java                (Account <-> AccountJpaEntity)
            │   │   │   │   ├── SpringDataAccountRepository.java (extends JpaRepository<AccountJpaEntity, UUID>)
            │   │   │   │   └── JpaAccountRepositoryAdapter.java  (implements AccountRepository)
            │   │   │   └── web/
            │   │   │       ├── AccountController.java
            │   │   │       └── dto/                              (OpenAccountRequest, AmountRequest, TransferRequest, AccountResponse, etc. — records)
            │   │   ├── statements/
            │   │   │   ├── domain/
            │   │   │   │   ├── StatementLine.java                (record — own vocabulary: String accountId, BigDecimal amount, String currencyCode)
            │   │   │   │   ├── StatementLineType.java            (enum: DEBIT, CREDIT)
            │   │   │   │   └── StatementRepository.java          (port interface)
            │   │   │   ├── infrastructure/
            │   │   │   │   ├── StatementLineJpaEntity.java
            │   │   │   │   ├── StatementLineMapper.java
            │   │   │   │   ├── SpringDataStatementRepository.java
            │   │   │   │   ├── JpaStatementRepositoryAdapter.java
            │   │   │   │   └── acl/
            │   │   │   │       └── LedgerEventTranslator.java    (@Component, @EventListener(LedgerEvent), sealed switch -> StatementLine)
            │   │   │   └── web/
            │   │   │       └── StatementController.java
            │   │   └── web/
            │   │       └── DomainExceptionHandler.java           (@RestControllerAdvice, shared by both contexts)
            │   └── resources/
            │       └── application.yml                           (server.port: 8099; H2 file/mem datasource)
            └── test/
                ├── java/com/testingai/banking/
                │   ├── BankingDemoApplicationTest.java
                │   ├── ledger/
                │   │   ├── domain/
                │   │   │   ├── AccountTest.java                  (invariants: insufficient funds, invalid amount, currency mismatch)
                │   │   │   ├── MoneyTest.java                    (arithmetic, mismatched-currency rejection)
                │   │   │   └── TransferServiceTest.java           (correlated withdraw+deposit, transferId propagation)
                │   │   └── application/                          (use case tests against an in-memory AccountRepository fake)
                │   ├── statements/
                │   │   └── infrastructure/acl/
                │   │       └── LedgerEventTranslatorTest.java     (each LedgerEvent variant -> correct StatementLine)
                │   ├── web/
                │   │   ├── BankingIntegrationTest.java            (MockMvc golden path: open -> deposit -> withdraw -> transfer -> statement reflects all of it)
                │   │   └── BankingErrorPathTest.java              (MockMvc: withdraw > balance -> 400; unknown account -> 404)
                │   └── performance/
                │       └── BankingSimulation.java
                └── resources/application.yml
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep to also match `^domain-driven-design/.*\.java$`, and add a matching `mvn spotless:apply` block run from `domain-driven-design/`.
- **`CLAUDE.md`** — add a "DDD banking ledger demo" command section (mirroring the saga pattern section), a `domain-driven-design/` row in the repository layout table, and a line noting no infrastructure/docker is required.
- **`README.md`** — add `domain-driven-design/` to the top-level repository layout table.

## Ledger context (`com.testingai.banking.ledger`)

**Aggregate root — `Account`** (plain class, not a record: it has identity and lifecycle):
- Fields: `AccountId id`, `String ownerName`, `Money balance`
- `static Account open(String ownerName, Money initialBalance)` — registers `AccountOpened`
- `void deposit(Money amount)` — rejects non-positive amount (`InvalidAmountException`) and currency mismatch (`CurrencyMismatchException`); registers `MoneyDeposited`
- `void withdraw(Money amount)` — same rejections plus `InsufficientFundsException` when `amount > balance`; registers `MoneyWithdrawn`
- Extends `AggregateRoot<AccountId>`, a small domain-owned base class (not Spring's) holding a `List<LedgerEvent>` with `registerEvent(...)` and `pullDomainEvents()` (returns and clears the list)

**Value objects (records):**
- `AccountId(UUID value)`
- `Money(BigDecimal amount, Currency currency)` — `plus`/`minus` throw `CurrencyMismatchException` on differing currencies

**Domain events — sealed interface, each variant a record:**
```java
public sealed interface LedgerEvent permits AccountOpened, MoneyDeposited, MoneyWithdrawn {}
public record AccountOpened(AccountId accountId, String ownerName, Money openingBalance, Instant occurredAt) implements LedgerEvent {}
public record MoneyDeposited(AccountId accountId, Money amount, Instant occurredAt) implements LedgerEvent {}
public record MoneyWithdrawn(AccountId accountId, Money amount, Instant occurredAt) implements LedgerEvent {}
```

**Domain service — `TransferService`:** a transfer spans two aggregates, so it cannot live inside a single `Account`. It withdraws from the source account, deposits into the target account, and tags both resulting events with a shared `transferId` (a `UUID` generated once per transfer and threaded through both calls) so the statements context can later associate the pair as one transfer.

**Port — `AccountRepository`:** `Account save(Account account)`, `Optional<Account> findById(AccountId id)` — interface lives in `domain`, not `infrastructure`.

**Application layer:** `OpenAccountUseCase`, `DepositUseCase`, `WithdrawUseCase`, `TransferMoneyUseCase` — each loads the aggregate(s), invokes domain behavior, calls `repository.save(...)`, then — only after `save()` succeeds — pulls pending events via `pullDomainEvents()` and publishes each through `ApplicationEventPublisher`.

**Infrastructure:** `AccountJpaEntity` (the only JPA-annotated class in this context — `balanceAmount`/`balanceCurrency` as separate columns), `AccountMapper` (`Account` ↔ `AccountJpaEntity`), `SpringDataAccountRepository` (`JpaRepository`), `JpaAccountRepositoryAdapter implements AccountRepository`.

**Web:** `AccountController` — thin, delegates to use cases; request/response DTOs are separate records from the domain's `Money`/`AccountId`.

## Statements context (`com.testingai.banking.statements`)

Deliberately does **not** reuse ledger's `Account`, `Money`, or `AccountId` types — that separation is the point of the exercise.

```java
public record StatementLine(
    UUID id,
    String accountId,
    StatementLineType type,
    BigDecimal amount,
    String currencyCode,
    String description,
    Instant occurredAt
) {}
public enum StatementLineType { DEBIT, CREDIT }
```

**Port — `StatementRepository`:** `void save(StatementLine line)`, `List<StatementLine> findByAccountId(String accountId)`.

**Anti-corruption layer — `LedgerEventTranslator`:** a `@Component` in `statements.infrastructure.acl` with an `@EventListener` method on `LedgerEvent` that pattern-matches over the sealed hierarchy (record patterns, exhaustive switch, no default branch needed) to build the appropriate `StatementLine`, then saves it via `StatementRepository`:

```java
@EventListener
void onLedgerEvent(LedgerEvent event) {
    StatementLine line = switch (event) {
        case AccountOpened(var id, var owner, var opening, var at) ->
            new StatementLine(UUID.randomUUID(), id.value().toString(), CREDIT,
                opening.amount(), opening.currency().getCurrencyCode(), "Account opened", at);
        case MoneyDeposited(var id, var amount, var at) ->
            new StatementLine(UUID.randomUUID(), id.value().toString(), CREDIT,
                amount.amount(), amount.currency().getCurrencyCode(), "Deposit", at);
        case MoneyWithdrawn(var id, var amount, var at) ->
            new StatementLine(UUID.randomUUID(), id.value().toString(), DEBIT,
                amount.amount(), amount.currency().getCurrencyCode(), "Withdrawal", at);
    };
    statementRepository.save(line);
}
```

This listener is the entire ACL: it is the only class in the codebase that imports both ledger's `LedgerEvent` hierarchy and statements' `StatementLine`. If ledger's model changes shape, only this translator needs to change.

**Infrastructure:** `StatementLineJpaEntity` + mapper + `SpringDataStatementRepository` + `JpaStatementRepositoryAdapter`, same H2 database, separate table.

**Web:** `StatementController` — `GET /accounts/{accountId}/statement` reads exclusively from `StatementRepository`, never from ledger's tables, proving the read path is fully decoupled.

## API surface

| Method & path | Use case | Success | Failure |
|---|---|---|---|
| `POST /accounts` `{ownerName, initialBalance, currency}` | `OpenAccountUseCase` | `201` + `{accountId}` | `400` invalid amount |
| `GET /accounts/{id}` | direct repository read | `200` + `{accountId, ownerName, balance, currency}` | `404` unknown account |
| `POST /accounts/{id}/deposits` `{amount, currency}` | `DepositUseCase` | `200` + updated balance | `400` invalid amount / currency mismatch; `404` unknown account |
| `POST /accounts/{id}/withdrawals` `{amount, currency}` | `WithdrawUseCase` | `200` + updated balance | `400` insufficient funds / invalid amount / currency mismatch; `404` unknown account |
| `POST /transfers` `{fromAccountId, toAccountId, amount, currency}` | `TransferMoneyUseCase` | `200` + `{transferId}` | `400` insufficient funds / currency mismatch; `404` unknown account |
| `GET /accounts/{id}/statement` | `StatementRepository.findByAccountId` | `200` + `List<StatementLine>` | — (empty list if none yet) |

Swagger UI at `/swagger-ui/index.html`, matching every other module.

## Error handling

`DomainException` (abstract, extends `RuntimeException`) is the base of `InsufficientFundsException`, `InvalidAmountException`, `CurrencyMismatchException`, and `AccountNotFoundException`. `DomainExceptionHandler` (`@RestControllerAdvice`, shared by both contexts):

- `InsufficientFundsException` / `InvalidAmountException` / `CurrencyMismatchException` → **400 Bad Request**, body `{error, message}` — mirrors the repo's existing convention of classifying business-rule rejections as `BAD_REQUEST` (see the GraphQL depth/complexity limiting work).
- `AccountNotFoundException` → **404 Not Found**.

Unmapped exceptions are not caught here and surface as a real `500`, consistent with the rest of the repo.

No `util/FailureSimulator` — this module's "failures" are the domain invariants themselves (insufficient funds, currency mismatch, invalid amount), which is a stronger DDD teaching point than random failure injection; the `FailureSimulator` convention in `.claude/rules/code-review.md` is scoped to `message-brokers/` and does not apply here.

## Testing

- **Domain unit tests** (pure Java, no Spring context): `AccountTest` (open/deposit/withdraw happy paths and every invariant violation), `MoneyTest` (arithmetic, mismatched-currency rejection), `TransferServiceTest` (withdraw+deposit correlation, shared `transferId`).
- **ACL unit test:** `LedgerEventTranslatorTest` — each `LedgerEvent` variant produces the correct `StatementLine` (type, amount, description).
- **Application-layer tests:** each use case tested against an in-memory fake `AccountRepository` (no Spring context needed).
- **Web integration test** (`@SpringBootTest` + `MockMvc`, H2, plain JUnit5/Spring — no Spock, per [[spock-spring-webmvctest-incompatibility]]): `BankingIntegrationTest` covers the full golden path — open account → deposit → withdraw → transfer between two accounts → `GET .../statement` reflects all of it correctly. This is the test that actually proves the event→ACL→read-model wiring works end to end.
- **Error-path integration test:** `BankingErrorPathTest` — withdraw beyond balance → `400` with the right body; unknown account → `404`.
- **Gatling load test:** `src/test/.../performance/BankingSimulation.java`, excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly via `mvn gatling:test` — open/deposit/withdraw/transfer/statement scenario mix.

## Ports

- `banking/spring-demo` → `8099` (next free slot after `websockets/spring-demo`'s `8098`).

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`banking-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `com.h2database:h2` (runtime — the app itself runs on H2, not just tests), `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test). No message broker, no Postgres, no docker-compose.

## README

`domain-driven-design/banking/spring-demo/README.md` follows the saga module's format: prerequisites (Java 21, Maven — no Docker needed), run instructions (`mvn spring-boot:run`), a diagram of the two bounded contexts and the ACL boundary, a patterns-demonstrated table (aggregate/value object/domain event/repository port/domain service/anti-corruption layer, each with the class that demonstrates it), and full `curl` walkthroughs: open two accounts, deposit, a withdrawal that fails on insufficient funds (400), a transfer between them, and the resulting statement for each account showing the ACL-translated entries.

`domain-driven-design/README.md` is a short category index (analogous to `distributed-transactions/README.md`), explaining the tactical DDD patterns on display and the ledger/statements/ACL structure, ready to grow if more DDD-flavored demos are added later (e.g. a Specification-pattern or bounded-context-integration-via-broker variant).

## Scope limits

- No message broker between contexts — the ACL listens via in-process `ApplicationEventPublisher`/`@EventListener`, not Kafka/RabbitMQ; that transport mechanic is already covered by `message-brokers/`. Called out explicitly in the README as a deliberate simplification.
- No event sourcing / no Axon — `cqrs-event-sourcing/axon` already covers that; this module's aggregates are current-state persisted (JPA), not event-sourced, so the two modules teach different things without overlapping.
- No Specification pattern, no additional bounded contexts beyond `ledger`/`statements` — kept to two contexts and one ACL so the boundary is the focus, not a sprawl of contexts.
- No random `FailureSimulator`-style failure injection — failures are the domain invariants themselves, deterministic and directly triggerable via the API (e.g. withdraw more than the balance), which is a stronger and more literal demonstration for this pattern.
- No currency conversion — `Money` arithmetic across differing currencies is rejected outright (`CurrencyMismatchException`) rather than converted; introducing exchange rates would add unrelated complexity to what this demo is built to teach.
