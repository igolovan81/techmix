# jOOQ Data-Access Demo Design

**Date:** 2026-09-03
**Status:** Draft

## Overview

A new top-level category `data-access/`, sibling to `noSQL/`, `template-engines/`, etc., holding demos for data-access-layer libraries/patterns (room for e.g. a MyBatis demo later). It starts with one module: `data-access/jooq/spring-demo`, a Spring Boot app built around a fresh e-commerce catalog schema (`category`, `product`, `customer`, `orders`, `order_item`) that demonstrates jOOQ's type-safe SQL DSL, generated code, joins/aggregations/window functions, dynamic query building, `MULTISET` nested results, batch operations, and transactions — including a Postgres stored function called through jOOQ.

The central mechanism worth calling out up front: **one `schema.sql` file is the single source of truth for the table DDL**, consumed three ways — Spring Boot bootstraps it against H2 (`MODE=PostgreSQL`) for tests, against real Postgres for `spring-boot:run`, and jOOQ's `DDLDatabase` codegen parses the same file (no live DB connection) to generate typed table classes at `generate-sources` time. This keeps `mvn clean package`/`mvn test` infra-free, matching every other module's convention — only running the app (and one stored-function test, below) needs Docker.

## Repository structure

```
data-access/
├── README.md                                          (new, modeled on noSQL/README.md)
├── pom.xml                                             (new parent reactor, modeled on noSQL/pom.xml)
└── jooq/
    ├── docker/
    │   └── docker-compose.yml                          (single Postgres container, :5434)
    └── spring-demo/
        ├── pom.xml                                     (artifactId: jooq-demo)
        └── src/
            ├── main/
            │   ├── java/com/testingai/jooq/
            │   │   ├── JooqDemoApplication.java
            │   │   ├── dsl/
            │   │   │   ├── ProductService.java          (CRUD baseline against generated PRODUCT table)
            │   │   │   ├── CategoryService.java          (create category — needed before a product can reference one)
            │   │   │   └── CustomerService.java           (create customer — needed before an order can reference one)
            │   │   ├── joins/
            │   │   │   ├── CatalogQueryService.java        (product⋈category listing; category summary aggregation; price-rank window function)
            │   │   │   ├── ProductWithCategory.java         (record)
            │   │   │   ├── CategorySummary.java              (record: categoryId, name, productCount, totalStock, avgPrice)
            │   │   │   └── RankedProduct.java                 (record: product fields + rank)
            │   │   ├── search/
            │   │   │   ├── ProductSearchService.java          (dynamic Condition assembly)
            │   │   │   └── ProductSearchCriteria.java          (record: category, minPrice, maxPrice, inStockOnly, nameContains — all nullable/optional)
            │   │   ├── nested/
            │   │   │   ├── OrderQueryService.java              (MULTISET: order + nested order_item rows in one query)
            │   │   │   ├── OrderWithItems.java                  (record: id, customerId, placedAt, List<OrderItemView> items)
            │   │   │   └── OrderItemView.java                    (record: productId, productName, quantity, unitPrice)
            │   │   ├── batch/
            │   │   │   └── OrderItemBatchService.java             (batchInsert order_item rows in one round trip)
            │   │   ├── transactions/
            │   │   │   ├── OrderPlacementService.java             (dslContext.transaction: stock check via SELECT ... FOR UPDATE, decrement, insert order + items; FailureSimulator-driven rollback demo)
            │   │   │   ├── PlaceOrderRequest.java                  (record: customerId, List<OrderLineRequest>)
            │   │   │   ├── OrderLineRequest.java                    (record: productId, quantity)
            │   │   │   ├── InsufficientStockException.java
            │   │   │   └── OrderTotalService.java                   (calls the Postgres calculate_order_total(order_id) function via DSL.function — the one Postgres-only piece)
            │   │   ├── util/
            │   │   │   └── FailureSimulator.java                     (FAILURE_RATE = 0.05, maybeThrow(String context) — same shape as message-brokers/kafka's)
            │   │   └── controller/
            │   │       └── DemoController.java
            │   └── resources/
            │       ├── db/
            │       │   ├── schema.sql                                (portable DDL — category, product, customer, orders, order_item; also the DDLDatabase codegen input)
            │       │   └── postgres-functions.sql                     (calculate_order_total — Postgres-only, PL/pgSQL)
            │       ├── application.yml                                (default profile: H2 MODE=PostgreSQL; server.port 8104)
            │       └── application-postgres.yml                        (postgres profile: real datasource; schema-locations adds postgres-functions.sql)
            └── test/
                ├── java/com/testingai/jooq/
                │   ├── JooqDemoApplicationTest.java
                │   ├── dsl/ProductServiceTest.java
                │   ├── joins/CatalogQueryServiceTest.java
                │   ├── search/ProductSearchServiceTest.java
                │   ├── nested/OrderQueryServiceTest.java
                │   ├── batch/OrderItemBatchServiceTest.java
                │   ├── transactions/OrderPlacementServiceTest.java
                │   ├── transactions/OrderTotalServiceIT.java           (Testcontainers Postgres — the one test needing Docker)
                │   ├── controller/DemoControllerTest.java
                │   └── performance/DemoSimulation.java
                └── resources/application.yml                            (H2 test config, mirrors main default profile)
```

### Cross-cutting edits to existing files

- **`README.md`** (repo root) — add a `data-access/` row to the repository layout table.
- **`CLAUDE.md`** — add a "jOOQ demo (data-access)" commands section (build/test/run/gatling, plus the one `mvn test -Dtest=OrderTotalServiceIT` Docker-requiring exception) and a `data-access/jooq/spring-demo/` row in the architecture table.
- **`.githooks/pre-commit`** — add a `data-access/` block (grep + `mvn spotless:apply` in `data-access/`), matching the existing per-category blocks; also add `data-access` to the top `grep -E` prefix alternation.

## Domain model

```sql
CREATE TABLE category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE product (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES category(id),
    name VARCHAR(200) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL
);

CREATE TABLE customer (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    placed_at TIMESTAMP NOT NULL
);

CREATE TABLE order_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL
);
```

Table is named `orders`, not `order`, since `ORDER` is a reserved SQL keyword. `GENERATED ALWAYS AS IDENTITY` is used instead of Postgres's `SERIAL` or H2-specific syntax because both databases and jOOQ's `DDLDatabase` parser accept the ANSI form, keeping the one file portable across all three consumers.

`postgres-functions.sql` (Postgres profile only, not fed to codegen):

```sql
CREATE OR REPLACE FUNCTION calculate_order_total(p_order_id BIGINT)
RETURNS DECIMAL(12,2) AS $$
    SELECT COALESCE(SUM(quantity * unit_price), 0)
    FROM order_item
    WHERE order_id = p_order_id;
$$ LANGUAGE sql;
```

## jOOQ code generation

`jooq-codegen-maven` bound to `generate-sources`, configured with `org.jooq.meta.extensions.ddl.DDLDatabase` pointed at `src/main/resources/db/schema.sql` — this parses the DDL directly, so codegen needs no running database and no Docker. Generated classes land in `target/generated-sources/jooq` under `com.testingai.jooq.generated`, auto-added to the compile source root by the plugin. Stored functions are **not** generated as jOOQ `Routines` — `DDLDatabase`'s PL/pgSQL parsing is unreliable, and the one function this demo needs is called directly via `DSL.function("calculate_order_total", SQLDataType.NUMERIC, DSL.val(orderId))`, which needs no generated binding.

`spring-boot-starter-jooq` auto-configures the `DSLContext` (and `SQLDialect`) from whichever datasource is active — H2 by default, Postgres under the `postgres` profile — so no manual dialect wiring is needed.

## Pattern implementations

### `dsl/` — ProductService, CategoryService, CustomerService

Plain generated-code CRUD (`ctx.insertInto(PRODUCT)...`, `ctx.selectFrom(PRODUCT).where(...)`, `ctx.update(PRODUCT)...`, `ctx.deleteFrom(PRODUCT)...`) — the baseline every other package builds on, same role as the Cassandra module's `crud/ProductService`.

### `joins/` — CatalogQueryService

- `listProductsWithCategory()` — `ctx.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE, CATEGORY.NAME).from(PRODUCT).join(CATEGORY).on(PRODUCT.CATEGORY_ID.eq(CATEGORY.ID))`, mapped into `ProductWithCategory`.
- `categorySummary()` — `GROUP BY category`, `COUNT(*)`, `SUM(stock)`, `AVG(price)`, mapped into `CategorySummary`.
- `rankProductsByPriceWithinCategory()` — `DSL.rank().over(DSL.partitionBy(PRODUCT.CATEGORY_ID).orderBy(PRODUCT.PRICE.desc()))`, mapped into `RankedProduct` — the window-function showcase.

### `search/` — ProductSearchService

Builds a `List<Condition>` from the non-null fields of `ProductSearchCriteria` (category → `PRODUCT.CATEGORY_ID.eq(...)`, minPrice/maxPrice → `PRODUCT.PRICE.ge/.le(...)`, inStockOnly → `PRODUCT.STOCK.gt(0)`, nameContains → `PRODUCT.NAME.likeIgnoreCase(...)`), then `ctx.selectFrom(PRODUCT).where(DSL.and(conditions))`. This is jOOQ's answer to the awkwardness of conditionally composing JPA Criteria/JPQL — the README calls this out explicitly as the pattern's point.

### `nested/` — OrderQueryService

`findOrderWithItems(orderId)` — one query using `MULTISET` to nest `order_item ⋈ product` rows inside each `orders` row:

```java
ctx.select(
      ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.PLACED_AT,
      multiset(
          select(ORDER_ITEM.PRODUCT_ID, PRODUCT.NAME, ORDER_ITEM.QUANTITY, ORDER_ITEM.UNIT_PRICE)
              .from(ORDER_ITEM)
              .join(PRODUCT).on(ORDER_ITEM.PRODUCT_ID.eq(PRODUCT.ID))
              .where(ORDER_ITEM.ORDER_ID.eq(ORDERS.ID))
      ).convertFrom(r -> r.map(Records.mapping(OrderItemView::new)))
  )
  .from(ORDERS)
  .where(ORDERS.ID.eq(orderId))
  .fetchOne(Records.mapping(OrderWithItems::new));
```

One round trip instead of the classic N+1 (or a manual join-then-group-in-memory) that this query would otherwise require.

### `batch/` — OrderItemBatchService

`addItemsBatch(orderId, List<OrderLineRequest>)` — `ctx.batch(lines.stream().map(l -> ctx.insertInto(ORDER_ITEM, ...).values(...)).toList()).execute()`, one batch round trip instead of N individual inserts.

### `transactions/` — OrderPlacementService, OrderTotalService

`OrderPlacementService.placeOrder(request)` runs inside `ctx.transactionResult(configuration -> { ... })`:
1. `FailureSimulator.maybeThrow("order-placement")` — 5% induced failure, demonstrating the whole transaction rolls back (same `FAILURE_RATE`/`maybeThrow(String)` shape as `message-brokers/kafka`'s `FailureSimulator`, per `.claude/rules/code-review.md`'s consistency rule, applied here for the same reason: a uniform, recognizable failure-injection shape across the repo, even though that rule's text scopes literally to `message-brokers/`).
2. Insert the `orders` row.
3. For each line: `DSL.using(configuration).selectFrom(PRODUCT).where(PRODUCT.ID.eq(productId)).forUpdate().fetchOne()` (pessimistic row lock — the transaction-safety showcase), throw `InsufficientStockException` if `stock < quantity`, else update the row's stock and insert the `order_item`.

`OrderTotalService.getTotal(orderId)` — `ctx.select(DSL.function("calculate_order_total", SQLDataType.NUMERIC, DSL.val(orderId))).fetchOne(...)`. This is the one piece of the demo that needs real Postgres (PL/pgSQL isn't portable to H2), so it's isolated in its own service and its own test class (`OrderTotalServiceIT`, Testcontainers) rather than mixed into `OrderPlacementServiceTest`, which stays H2-only.

## API surface

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `POST /demo/categories` | Create a category. |
| `POST /demo/customers` | Create a customer. |
| `POST /demo/products` | Create a product (`dsl`). |
| `GET /demo/products/{id}` | Read a product (`dsl`). |
| `PUT /demo/products/{id}` | Update a product (`dsl`). |
| `DELETE /demo/products/{id}` | Delete a product (`dsl`). |
| `GET /demo/products/with-category` | Product⋈category listing (`joins`). |
| `GET /demo/categories/summary` | Per-category aggregation (`joins`). |
| `GET /demo/products/ranked` | Price rank within category, window function (`joins`). |
| `GET /demo/products/search?category=&minPrice=&maxPrice=&inStockOnly=&nameContains=` | Dynamic filter query (`search`). |
| `POST /demo/orders` | Place an order — stock-locking transaction, may 409 on insufficient stock or roll back on simulated failure (`transactions`). |
| `GET /demo/orders/{id}` | Order with nested items via `MULTISET` (`nested`). |
| `POST /demo/orders/{id}/items/batch` | Batch-insert additional order items (`batch`). |
| `GET /demo/orders/{id}/total` | Order total via the Postgres stored function (`transactions`, Postgres-only). |

Swagger UI at `/swagger-ui/index.html`, same as every other module.

## Testing

- Unit/integration tests per service run against a real **H2** datasource (`MODE=PostgreSQL`), not a mocked `DSLContext` — jOOQ's fluent `select`/`from`/`where`/`join` builder chain isn't practically mockable the way `MongoTemplate`/`CassandraTemplate` are in the NoSQL modules, so this module's tests seed minimal rows and assert against real generated-code queries instead. This is a deliberate, jOOQ-specific departure from those modules' testing convention, not an inconsistency, and it needs no Docker (H2 is in-memory).
- `DemoControllerTest` — `@WebMvcTest(DemoController.class)` + `MockMvc` + `@MockitoBean` per service, matching the repo-wide controller-test convention.
- `OrderTotalServiceIT` — the one exception: `@Testcontainers` + `PostgreSQLContainer`, `@ActiveProfiles("postgres")`, `@DynamicPropertySource` overriding the JDBC URL to the ephemeral container. Excluded from the default `mvn test` run via the same surefire `<excludes>` mechanism already used for Gatling (`**/performance/**`), extended with `**/*IT.java`; run explicitly with `mvn test -Dtest=OrderTotalServiceIT` (requires only a working Docker daemon — Testcontainers manages the container itself, no manual `docker compose` step needed for this one test).
- `src/test/.../performance/DemoSimulation.java` — Gatling load test hitting the endpoints above; excluded from `mvn test` via the inherited `**/performance/**` exclude, run explicitly via `mvn gatling:test`.

## Ports

- `jooq/spring-demo` app → `8104` (next free slot after `batch-processing/spring-batch`'s `8103`).
- Postgres (docker compose) → `5434` (no collision with the shared stack's `5432` or `communication-protocols/graphql`'s `5433`).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (inherited from the new `data-access/pom.xml` parent, matching every other category's parent).
**Java:** 21

**`jooq-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-jooq`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `org.postgresql:postgresql` (runtime), `com.h2database:h2` (test), `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter` (test), `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test). `jooq-codegen-maven` plugin depends on `org.jooq:jooq-meta-extensions` at its own (build-time) scope for `DDLDatabase`.

`application.yml` (default — H2):
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:jooqdemo;MODE=PostgreSQL
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql

server:
  port: 8104
```

`application-postgres.yml` (real Postgres via docker compose or Testcontainers):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/jooqdemo
    username: jooq
    password: jooq
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql,classpath:db/postgres-functions.sql
```

`spring-boot-starter-jooq` auto-detects `SQLDialect` from the active datasource, so no explicit dialect configuration is needed in either profile.

## README

`data-access/README.md` mirrors `noSQL/README.md`'s shape: a one-line intro plus a table (currently one row: jOOQ). `data-access/jooq/README.md` (or `spring-demo/README.md`, matching whichever level other single-module categories document at) covers: prerequisites, `docker compose up` for Postgres, build/run/test commands (including the `OrderTotalServiceIT` Docker caveat), `curl` walkthroughs per pattern, and a short note on the schema.sql-as-single-source-of-truth mechanism since it's the module's most distinctive architectural choice.

## Scope limits

- No jOOQ `Routines` codegen for stored procedures/functions — the one Postgres function is called via `DSL.function(...)` directly; this is called out as a deliberate simplification, not a missing feature.
- No multi-datasource / read-replica demo — out of scope, this module is about query-building patterns, not scaling topology.
- If `MULTISET` or `FOR UPDATE` locking prove flaky under H2 during implementation, the fallback is moving that one test to the Testcontainers/Postgres path (like `OrderTotalServiceIT`) rather than dropping the pattern — confirm with the user only if actually needed.
- `postgres-functions.sql` is intentionally excluded from `DDLDatabase` codegen input; it must stay free of anything that would change the generated table API (i.e., no `ALTER TABLE`), so the portable `schema.sql` remains the only table-shape source of truth.
