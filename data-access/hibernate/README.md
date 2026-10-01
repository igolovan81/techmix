# Hibernate Demo

A Spring Boot app demonstrating Hibernate/JPA mechanics that plain CRUD doesn't exercise, over a small library-lending domain (`author`, `library_item` [Book/Dvd/Magazine], `copy`, `person` [Member/Librarian], `loan`).

## Prerequisites

- Java 21, Maven
- Docker (only for the `postgres` profile and the one `CopyCheckoutServiceConcurrencyIT` test)

## Build, test, run

```bash
cd data-access/hibernate/spring-demo

mvn clean package                                        # build
mvn test                                                   # unit tests (H2) — no Docker needed
mvn test -Dtest=CopyCheckoutServiceConcurrencyIT             # the one Postgres-only test — needs a Docker daemon (Testcontainers)
mvn spring-boot:run                                         # run against H2, no Docker — app on :8105
```

To run against real Postgres instead:

```bash
docker compose -f data-access/hibernate/docker/docker-compose.yml up -d   # Postgres on :5435
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Patterns and endpoints

| Pattern | Endpoint(s) |
|---|---|
| Seed data | `POST /demo/authors`, `POST /demo/books`, `POST /demo/copies`, `POST /demo/members` |
| `associations` — N+1, `JOIN FETCH`, `@EntityGraph`, `@BatchSize` | `GET /demo/books/n-plus-one`, `GET /demo/books/join-fetch`, `GET /demo/books/entity-graph`, `GET /demo/library-items/batch-fetch` |
| `caching` — L1 vs L2 | `GET /demo/authors/{id}/cache/l1`, `GET /demo/authors/{id}/cache/l2` |
| `locking` — optimistic vs pessimistic | `POST /demo/copies/{copyId}/checkout/optimistic?memberId=`, `POST /demo/copies/{copyId}/checkout/pessimistic?memberId=` (may 409 if the copy is already on loan, or roll back on a simulated 5% failure) |
| `inheritance` — polymorphic queries | `GET /demo/library-items`, `GET /demo/library-items/books`, `GET /demo/library-items/dvds`, `GET /demo/library-items/magazines`, `GET /demo/people` |

Swagger UI: `http://localhost:8105/swagger-ui/index.html`.

## Load testing

```bash
mvn gatling:test    # requires the app running first (default H2 profile is fine)
```
