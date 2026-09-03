# Hibernate Data-Access Demo — Design

## Goal

Add `data-access/hibernate/spring-demo` as a second module in the `data-access/` reactor (sibling to `data-access/jooq/spring-demo`), a Spring Boot app demonstrating Hibernate/JPA-specific mechanics that `backend/rest-api`'s plain CRUD usage doesn't exercise: association fetching strategies and the N+1 problem, first- and second-level caching, optimistic and pessimistic concurrency control, and advanced mapping (inheritance strategies, embeddables, attribute converters, entity auditing) — over a library lending domain.

## Why this domain, why these features

`backend/rest-api` already demonstrates ordinary JPA CRUD with Liquibase-managed schema. This module exists to show the parts of Hibernate that only show up under load, under concurrency, or with a non-trivial object graph — the reasons teams reach for Hibernate specifically rather than a thinner data-mapper. A library lending system supplies natural motivation for every chosen feature: books have multiple authors (association/N+1), physical copies are checked out under contention (locking), the catalog holds distinct media types and people hold distinct roles (inheritance), and loan records are audited events (auditing).

## Tech stack

Spring Boot 3.4.4, Java 21, `spring-boot-starter-data-jpa` (Hibernate 6.6.x as bundled), `spring-boot-starter-web`, H2 (`MODE=PostgreSQL`, default profile — no Docker) / PostgreSQL 16 (`postgres` profile, Docker), `hibernate-jcache` + `com.github.ben-manes.caffeine:caffeine-jcache` for L2 region caching, Testcontainers (for the one concurrency test that needs real row-level locking), Lombok, springdoc-openapi, Gatling — same versions/conventions as `data-access/jooq/spring-demo`.

## Module scaffolding

- `data-access/pom.xml` — add `<module>hibernate/spring-demo</module>`
- `data-access/hibernate/spring-demo/pom.xml` — artifactId `hibernate-demo`, base package `com.testingai.hibernate`
- `data-access/hibernate/docker/docker-compose.yml` — Postgres 16, container `hibernate-postgres`, db/user/password `hibernate`, host port **5435** (jOOQ occupies 5434)
- App port **8105** (next free after jOOQ's 8104)
- `hibernate.ddl-auto: none` in both profiles — schema is owned by a hand-written `src/main/resources/db/schema.sql` (idempotent `CREATE TABLE IF NOT EXISTS`, `spring.sql.init.mode: always`), loaded against H2 by default and against Postgres under the `postgres` profile — identical bootstrap approach to the jOOQ module. Hibernate maps onto this schema; it does not generate it. This matters here specifically because it keeps the mapping annotations (inheritance strategy, discriminator column, `@Version` column, join table) honest against a schema that isn't auto-derived from them.

## Domain model / schema

```
author(id, name)
book_author(book_id, author_id)              -- join table, Book <-> Author many-to-many

library_item(id, item_type, title, publication_year,   -- SINGLE_TABLE inheritance, discriminator = item_type
             isbn,                                      -- Book only
             region_code, runtime_minutes,               -- Dvd only
             issue_number)                                -- Magazine only

copy(id, library_item_id -> library_item, barcode, status, version)
                                                          -- status: AVAILABLE | ON_LOAN | LOST
                                                          -- version: optimistic-locking column

person(id, person_type, name, email)                     -- JOINED inheritance, discriminator = person_type
member(person_id -> person, membership_date, street, city, zip_code)
                                                          -- street/city/zip_code back an Address @Embeddable
librarian(person_id -> person, employee_id, department)

loan(id, copy_id -> copy, member_id -> member, loan_date, due_date, return_date,
     created_at, updated_at)                              -- created_at/updated_at via @CreatedDate/@LastModifiedDate
```

**Inheritance choice:** two strategies are demonstrated on domain-appropriate hierarchies rather than three contrived ones sharing a single hierarchy (a hierarchy has one physical strategy — showing three would mean three unrelated toy hierarchies, which teaches the annotation but not why you'd pick one). `LibraryItem` uses `SINGLE_TABLE` (polymorphic catalog and loan lookups never need a join, and the wasted nullable columns per subtype are the textbook trade-off worth showing). `Person` uses `JOINED` (Member and Librarian have enough disjoint, non-nullable-in-practice columns that a normalized per-subtype table is the more honest mapping, and it contrasts directly with `SINGLE_TABLE` next to it). `TABLE_PER_CLASS` is not implemented.

**Value objects:** `Isbn` is a small validated wrapper (`AttributeConverter<Isbn, String>`) on `Book.isbn` — a single-column value type. `Address` is an `@Embeddable` (street/city/zipCode) on `Member` — a multi-column value type. This pairing shows both mapping tools on the value type shape each actually suits, rather than picking one arbitrarily for both.

## Pattern packages

Package-per-pattern, mirroring `data-access/jooq/spring-demo`'s `dsl/`, `joins/`, `search/`, `nested/`, `batch/`, `transactions/` layout:

- **`entity/`** — `LibraryItem`/`Book`/`Dvd`/`Magazine`, `Copy`, `Person`/`Member`/`Librarian`, `Author`, `Loan`, `Address` (`@Embeddable`), `Isbn` + `IsbnConverter`. Records are not usable here (JPA entities require mutable state and a no-arg constructor), so entities are Lombok-annotated classes (`@Getter`/`@Setter`, no `@Data` on entities with collections — avoid the classic `equals`/`hashCode`/collection recursion trap).
- **`repository/`** — thin `JpaRepository` interfaces (`BookRepository`, `MemberRepository`, `CopyRepository`, `LoanRepository`) for basic id/lookup access, used by the pattern services below rather than duplicated.
- **`associations/`** — `BookAuthorService`. `demonstrateNPlusOne()` fetches all books and touches `.getAuthors()` in a loop, returning a count of SQL statements executed (via Hibernate `Statistics`) alongside the result, so the test can assert the query count is `O(n)`. Three fixes, each its own method returning the same data with an assertably flat query count: `findAllWithJoinFetch()` (HQL `JOIN FETCH`), `findAllWithEntityGraph()` (`@EntityGraph`), `findAllWithBatchFetch()` (`@BatchSize` on the `authors` collection, still N+1 in query *count* but O(n/batch) in round trips — the method-level docs/test should make clear this is a different trade-off than the first two, not a strictly better one).
- **`caching/`** — `AuthorCacheService`, backed by `@org.hibernate.annotations.Cache(usage = READ_WRITE)` on `Author`. `readTwiceInSameTransaction(id)` demonstrates L1: asserts exactly one SQL `SELECT` via `Statistics` across two `repository.findById` calls inside one `@Transactional` method. `readAcrossTwoTransactions(id)` demonstrates L2: two separate transactional calls, first is a DB hit, second is an L2 cache hit — asserted via `Statistics#getSecondLevelCacheHitCount()`.
- **`locking/`** — `CopyCheckoutService`. `checkoutOptimistic(copyId, memberId)` reads a `Copy` (with its `@Version`), flips status, saves; under two concurrent callers on the same copy, the second commit throws `ObjectOptimisticLockingFailureException`, which the service translates to a domain `CopyAlreadyLoanedException`. `checkoutPessimistic(copyId, memberId)` uses `entityManager.find(Copy.class, id, LockModeType.PESSIMISTIC_WRITE)`, so the second concurrent caller blocks on the DB row lock and proceeds safely once the first transaction commits, rather than conflicting. Both are exercised by tests that fire concurrent calls via `ExecutorService` against two copies with one available unit each, asserting one caller succeeds and the other either throws (optimistic) or succeeds after waiting (pessimistic).
- **`inheritance/`** — `LibraryCatalogService` (polymorphic HQL over `LibraryItem`: `listAll()` returns mixed subtypes, `listBooks()`/`listDvds()`/`listMagazines()` use `TREAT`/discriminator-typed queries). `PersonDirectoryService` (polymorphic query over the JOINED `Person` hierarchy: `listAllPeople()` returns `Member`s and `Librarian`s from one query spanning the joined tables).
- **`util/FailureSimulator`** — same shape as `message-brokers/kafka/spring-demo/.../util/FailureSimulator.java` (`FAILURE_RATE = 0.05`, `maybeThrow(String context)`, no `shouldFail()`), applied inside `CopyCheckoutService`'s checkout methods.
- **`controller/DemoController`** — single REST controller wiring every pattern service to an endpoint, following `data-access/jooq/spring-demo/controller/DemoController`'s shape (inline static record request DTOs, one method per pattern-service method).

## Testing approach

- Per-pattern services: `@DataJpaTest` (H2, default profile, transactional rollback) — covers `associations/`, `caching/`, `inheritance/`, and the non-concurrent parts of `locking/`.
- `locking/CopyCheckoutServiceConcurrencyIT` — the one exception. True row-level pessimistic blocking isn't reliably testable against H2's locking semantics, so this test runs against Testcontainers Postgres via the `postgres` profile, named `*IT` and excluded from `mvn test` the same way `OrderTotalServiceIT` is excluded in the jOOQ module.
- `DemoControllerTest` — `@WebMvcTest(DemoController.class)` + `MockMvc` + `@MockitoBean` per service.
- `src/test/.../performance/DemoSimulation.java` — Gatling load test, excluded from `mvn test` via the inherited surefire config, run explicitly with `mvn gatling:test`.

## Documentation & wiring

- `data-access/hibernate/README.md` — mirrors `data-access/jooq/README.md`'s shape.
- `CLAUDE.md` — new command block for `data-access/hibernate/spring-demo`, mirroring the jOOQ block (build/test/run/postgres-profile/gatling commands), plus a row in the repository-layout table.
- `.claude/rules/code-review.md` — no changes needed; its `FailureSimulator` and modern-Java rules already apply repo-wide.
- `.githooks/pre-commit` — no change needed; it already matches staged Java files under the whole `data-access/` prefix and runs `mvn spotless:apply` from `data-access/`, which covers the new module automatically.

## Out of scope

- `TABLE_PER_CLASS` inheritance (see rationale above).
- Query result caching beyond entity-region L2 (no dedicated query-cache pattern package — one caching pattern package is enough to teach L1 vs L2 without a third mode diluting it).
- Any change to `backend/rest-api` — this is a standalone module, not a refactor of the existing JPA usage.
