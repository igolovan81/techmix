# Cassandra NoSQL Demo Design

**Date:** 2026-08-27
**Status:** Draft

## Overview

A new `noSQL/cassandra/` module, sibling to `noSQL/mongodb/`, following that module's established shape exactly: a Docker Compose cluster plus one Spring Boot demo app, sharing the product-catalog/orders domain for comparability. Where MongoDB's demo covers CRUD/transactions/change-streams/aggregation, Cassandra's demo covers six patterns that are actually distinctive to a wide-column store — MongoDB's four patterns don't map cleanly onto Cassandra (no joins, no ACID multi-document transactions, no native change streams):

- **crud** — single-partition CRUD on a `products` table
- **datamodeling** — query-first denormalization: the same order written to two tables (`orders_by_customer`, `orders_by_product`), one per query shape, instead of a join
- **lwt** — lightweight transactions: a Paxos-backed compare-and-set stock decrement (`IF stock >= ?`)
- **consistency** — tunable per-request consistency levels (`ONE`/`QUORUM`/`ALL`) on reads and writes against the 3-node ring
- **ttl** — expiring rows: a "recently viewed products" table written `USING TTL`
- **counter** — a dedicated counter table incremented on every order

`placeOrder` ties `lwt` + `datamodeling` + `counter` together in one call, mirroring how MongoDB's `OrderService.placeOrder` ties together transactions/change-streams/aggregation.

## Repository structure

```
noSQL/
├── README.md                                        (edit: add Cassandra row to the comparison table)
├── pom.xml                                           (edit: add <module>cassandra/spring-demo</module>)
└── cassandra/
    ├── README.md
    ├── docker/
    │   ├── docker-compose.yml
    │   ├── init/
    │   │   └── init-keyspace.cql                     (keyspace + all 6 tables, RF=3)
    │   ├── cassandra-metrics/
    │   │   ├── Dockerfile                            (FROM cassandra:5.0; downloads jmx_prometheus_javaagent 0.20.0)
    │   │   └── cassandra.yml                         (jmx_exporter config: catch-all rule)
    │   ├── prometheus/
    │   │   └── prometheus.yml
    │   └── grafana/
    │       ├── provisioning/
    │       │   ├── datasources/prometheus.yml
    │       │   └── dashboards/provider.yml
    │       └── dashboards/cassandra.json
    └── spring-demo/
        ├── pom.xml                                   (artifactId: cassandra-demo)
        └── src/
            ├── main/
            │   ├── java/com/testingai/cassandra/
            │   │   ├── CassandraDemoApplication.java
            │   │   ├── crud/
            │   │   │   ├── Product.java               (@Table("products"))
            │   │   │   └── ProductService.java
            │   │   ├── datamodeling/
            │   │   │   ├── OrderByCustomer.java        (@Table("orders_by_customer"))
            │   │   │   ├── OrderByProduct.java          (@Table("orders_by_product"))
            │   │   │   ├── OrderService.java             (placeOrder: ties lwt + datamodeling + counter together)
            │   │   │   └── PlaceOrderRequest.java        (record: customerId, productId, quantity)
            │   │   ├── lwt/
            │   │   │   └── StockReservationService.java  (conditional UPDATE ... IF stock >= ?)
            │   │   ├── consistency/
            │   │   │   ├── ConsistencyDemoService.java   (read/write products at a caller-supplied CL)
            │   │   │   └── ConsistencyReadResult.java    (record: product, consistencyLevel, elapsedMillis)
            │   │   ├── ttl/
            │   │   │   ├── ProductView.java              (@Table("recently_viewed"))
            │   │   │   └── RecentlyViewedService.java    (record on read, list still-live rows)
            │   │   ├── counter/
            │   │   │   └── OrderCountService.java        (raw CQL increment + read against order_counts_by_product)
            │   │   └── controller/
            │   │       └── DemoController.java
            │   └── resources/
            │       └── application.yml                  (server.port: 8085; spring.cassandra.*)
            └── test/
                ├── java/com/testingai/cassandra/
                │   ├── CassandraDemoApplicationTest.java
                │   ├── crud/ProductServiceTest.java
                │   ├── datamodeling/OrderServiceTest.java
                │   ├── lwt/StockReservationServiceTest.java
                │   ├── consistency/ConsistencyDemoServiceTest.java
                │   ├── ttl/RecentlyViewedServiceTest.java
                │   ├── counter/OrderCountServiceTest.java
                │   ├── controller/DemoControllerTest.java
                │   └── performance/DemoSimulation.java
                └── resources/application.yml
```

### Cross-cutting edits to existing files

- **`noSQL/pom.xml`** — add `<module>cassandra/spring-demo</module>`.
- **`noSQL/README.md`** — add a Cassandra row to the comparison table (`Cassandra | 3-node ring, RF=3 | Wide-column, write-heavy workloads, tunable consistency, linear scalability`).
- **`CLAUDE.md`** — add a "Cassandra demo" command section (mirroring the MongoDB section), a `noSQL/cassandra/spring-demo/` row in the repository layout table.
- **`README.md`** (repo root) — add/extend the `noSQL/` row if one exists there.
- **`.githooks/pre-commit`** — verify the existing `noSQL/` staged-file grep and `mvn spotless:apply` block already cover any path under `noSQL/`, since it's driven by the `noSQL/` prefix, not per-database; only extend if the hook is scoped to `noSQL/mongodb/` specifically.

## Cluster topology (Docker Compose)

Three `cassandra:5.0` nodes forming one ring, keyspace `ecommerce` with `RF=3` (matches the MongoDB demo's 3-node replica set, so the `consistency` pattern has real replicas to be tunable across):

| Service | Native transport (host:container) | Role |
|---|---|---|
| `cassandra1` | `9042:9042` | seed |
| `cassandra2` | `9043:9042` | |
| `cassandra3` | `9044:9042` | |
| `cassandra-init` | — | one-shot `cqlsh` container running `init-keyspace.cql` once all three nodes are healthy (mirrors `mongo-init`) |

`CASSANDRA_SEEDS=cassandra1` on all three nodes; `CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch`; healthcheck via `cqlsh -e "SELECT release_version FROM system.local"` (the standard Cassandra-readiness probe). No Cassandra web UI (mongo-express equivalent doesn't have a maintained analog); the README documents `cqlsh`/`nodetool` commands via `docker exec` instead, same as the message-broker demos that lack a UI (e.g. SQS/LocalStack). `nodetool` needs no host port mapping — it talks to the local node's JMX from inside the same container via `docker exec`.

### Monitoring

Each Cassandra node runs its own image built from a small `Dockerfile` (`FROM cassandra:5.0`) that downloads the `prometheus/jmx_exporter` Java agent jar (`io.prometheus.jmx:jmx_prometheus_javaagent`, pinned version `0.20.0`) at build time. `JVM_EXTRA_OPTS=-javaagent:/opt/jmx_prometheus_javaagent.jar=7070:/etc/jmx-exporter/cassandra.yml` attaches it directly to each node's JVM — no separate exporter sidecar/container, no reliance on remote JMX being reachable across containers. `cassandra.yml` (mounted read-only, one shared minimal config: a catch-all `- pattern: ".*"` rule, not curated per-metric) is the same for all three nodes. Each node exposes its Prometheus endpoint on `7070` internally (not published to the host, same as `mongodb-exporter`); Prometheus (`9096:9090`) scrapes all three by service name, Grafana (`3003:3000`) gets one provisioned dashboard (panels: node up/down per instance, read/write latency, pending compactions, storage load, JVM heap — the metrics the catch-all rule exposes). This is the part of the stack most likely to need iteration during implementation — attaching a javaagent to a base image via a custom Dockerfile is more moving parts than `mongodb-exporter`'s single ready-made image; if it proves unreliable, the fallback is monitoring `cassandra1` only (drop the other two `JVM_EXTRA_OPTS`, still demonstrates the pattern with one node's panels).

## Domain model

Same product-catalog/orders domain as `noSQL/mongodb`, with a `customerId` added to `PlaceOrderRequest` to justify the two denormalized order tables.

```sql
CREATE TABLE products (
    id uuid PRIMARY KEY,
    name text,
    price decimal,
    stock int
);

CREATE TABLE orders_by_customer (
    customer_id text,
    order_id timeuuid,
    product_id uuid,
    quantity int,
    unit_price decimal,
    line_total decimal,
    PRIMARY KEY (customer_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE orders_by_product (
    product_id uuid,
    order_id timeuuid,
    customer_id text,
    quantity int,
    unit_price decimal,
    line_total decimal,
    PRIMARY KEY (product_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE recently_viewed (
    product_id uuid,
    viewed_at timeuuid,
    PRIMARY KEY (product_id, viewed_at)
) WITH CLUSTERING ORDER BY (viewed_at DESC);
-- rows written with USING TTL <seconds>

CREATE TABLE order_counts_by_product (
    product_id uuid PRIMARY KEY,
    order_count counter
);
```

## Pattern implementations

### `crud/` — ProductService

Standard `CassandraTemplate` (Spring Data Cassandra) CRUD against `products`, single partition per product — the baseline every other pattern builds on.

### `datamodeling/` — OrderService

`placeOrder(customerId, productId, quantity)`:
1. Calls `StockReservationService.decrementIfAvailable` (the `lwt` pattern) — fails fast with a 409-mapped exception if the conditional update reports `[applied] = false`.
2. On success, writes the same order data into both `orders_by_customer` and `orders_by_product` via `CassandraTemplate` — no join, no secondary index, just two purpose-built tables, the signature Cassandra data-modeling move.
3. Calls `OrderCountService.increment` (the `counter` pattern).

`order_id` is a `timeuuid` generated once per order and reused across both denormalized rows and the counter, so the three writes stay identifiable as one logical order even though Cassandra gives no cross-table transaction.

### `lwt/` — StockReservationService

Cassandra does not support arithmetic (`col = col - ?`) on regular (non-counter) columns — only counter columns support increment/decrement in the `SET` clause. So this is a **read-then-CAS**, not an in-place decrement: read the current `Product` (via `CassandraTemplate.selectOneById`), fail fast with a stock-unavailable error if `stock < quantity`, otherwise compute `newStock = stock - quantity` and issue `UPDATE products SET stock = ? WHERE id = ? IF stock = ?` binding `(newStock, id, stock)` — the `IF stock = ?` compares against the *exact value just read*, guaranteeing the Paxos-backed conditional write only applies if no concurrent writer changed the row in between (classic compare-and-swap; binding the condition to a threshold like `IF stock >= ?` instead would silently allow lost updates, since the `SET` value is computed from a possibly-stale read while the `IF` would only re-check a threshold, not equality — this module deliberately avoids that bug). Uses the injected `CqlSession` directly (`SimpleStatement.newInstance(cql, newStock, id, stock)`, `resultSet.wasApplied()`) rather than Spring Data Cassandra's mapping layer, since IF-conditions on arbitrary columns aren't modeled by `CassandraOperations`. No retry loop on a lost CAS race — the demo surfaces the failure (`IllegalStateException`, matching how `noSQL/mongodb`'s `OrderService` throws on insufficient stock) rather than building retry infrastructure.

### `consistency/` — ConsistencyDemoService

Exposes a read that accepts a `ConsistencyLevel` parameter (`ONE`, `QUORUM`, `ALL`, parsed via `DefaultConsistencyLevel.valueOf(...)`), executed as `SimpleStatement.newInstance("SELECT id, name, price, stock FROM products WHERE id = ?", id).setConsistencyLevel(level)` against the injected `CqlSession` — consistency level is a per-statement driver setting, not something `CassandraOperations`' higher-level API exposes cleanly, so this uses the same raw-`CqlSession` approach as `lwt/`. Wraps the call with a timer and returns `ConsistencyReadResult` (product fields, level used, elapsed millis) so the README can show the latency/consistency trade-off concretely by hitting the same endpoint with different levels against the 3-node ring.

### `ttl/` — RecentlyViewedService

Every `GET /demo/products/{id}` call also inserts a row into `recently_viewed` `USING TTL 300` (5 minutes) via `SimpleStatement.newInstance("INSERT INTO recently_viewed (product_id, viewed_at) VALUES (?, ?) USING TTL 300", productId, Uuids.timeBased())` against the injected `CqlSession`. A separate listing endpoint reads back rows for a product — since Cassandra silently drops expired rows from query results, "still live" requires no extra filtering, which is itself worth calling out in the README as the point of the pattern. Recording the view is orchestrated by `DemoController` (calls `ProductService.findById` then `RecentlyViewedService.recordView`), not by `ProductService` itself, keeping `crud/` free of a dependency on `ttl/`.

### `counter/` — OrderCountService

`order_counts_by_product` is a counter table — Cassandra requires counter columns to live in a table containing only the counter(s) plus the primary key, and updates only via increment/decrement CQL, never a regular `UPDATE ... SET count = ?`. This service uses the injected `CqlSession` directly — `SimpleStatement.newInstance("UPDATE order_counts_by_product SET order_count = order_count + 1 WHERE product_id = ?", productId)` to increment, and a `SELECT` + `resultSet.one()` to read — since counters aren't modeled by `CassandraOperations` at all.

Combined, `lwt/`, `consistency/`, `ttl/`, and `counter/` all use the injected `CqlSession` (raw CQL via `SimpleStatement`) rather than `CassandraTemplate`, because each needs a capability — arbitrary-column IF-conditions, per-statement consistency override, TTL, or counter arithmetic — that Spring Data Cassandra's object-mapping layer doesn't model. `crud/` and `datamodeling/`'s table writes use `CassandraTemplate`, since those are plain entity reads/writes with no such requirement. This is a wider raw-CQL footprint than `noSQL/mongodb`'s single exception (none — Mongo's demo uses `MongoTemplate` throughout), called out explicitly in this module's README as a deliberate, Cassandra-specific departure, not an inconsistency.

## API surface

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `POST /demo/products` | Create a product; returns `Product`. |
| `GET /demo/products/{id}` | Read a product (also records a `recently_viewed` row via `ttl`); returns `Product`. |
| `PUT /demo/products/{id}` | Update a product; returns `Product`. |
| `DELETE /demo/products/{id}` | Delete a product. |
| `POST /demo/orders` | `PlaceOrderRequest` body → `lwt` decrement + `datamodeling` writes + `counter` increment; returns the placed order or 409 if stock unavailable. |
| `GET /demo/orders/by-customer/{customerId}` | Reads `orders_by_customer`; returns `List<OrderByCustomer>`. |
| `GET /demo/orders/by-product/{productId}` | Reads `orders_by_product`; returns `List<OrderByProduct>`. |
| `GET /demo/products/{id}/consistency?level=ONE\|QUORUM\|ALL` | Reads the product at the given consistency level; returns `ConsistencyReadResult`. |
| `GET /demo/products/{id}/recently-viewed` | Lists still-live `recently_viewed` rows for the product. |
| `GET /demo/products/{id}/order-count` | Reads the counter; returns the current count. |

Swagger UI at `/swagger-ui/index.html`, same as `noSQL/mongodb`.

## Testing

- Unit tests per service (`crud/`, `datamodeling/`, `lwt/`, `consistency/`, `ttl/`, `counter/`) — `@ExtendWith(MockitoExtension.class)` with `CassandraTemplate` (and, for `counter/`, `CqlOperations`) mocked via `@Mock`/`@InjectMocks`, exactly matching `noSQL/mongodb`'s `ProductServiceTest` pattern (`MongoTemplate` mocked, no live cluster). `mvn test` for this module does **not** require the Docker Compose cluster running — only `mvn gatling:test` and manual `curl` walkthroughs do.
- `DemoControllerTest` — `@WebMvcTest(DemoController.class)` + `MockMvc` + `@MockitoBean` per service, matching `noSQL/mongodb`'s own `DemoControllerTest` exactly (plain JUnit 5 — this repo's `noSQL` Java tests don't use Spock, so [[spock-spring-webmvctest-incompatibility]] doesn't apply here), one happy-path case per endpoint plus the 409-on-insufficient-stock case.
- `src/test/.../performance/DemoSimulation.java` — Gatling load test hitting all ten endpoints; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude (already configured at `noSQL/pom.xml` level), run explicitly via `mvn gatling:test`.

## Ports

- `cassandra/spring-demo` app → `8085` (next free slot after `mongodb/spring-demo`'s `8084`).
- Cassandra native transport → `9042`/`9043`/`9044` (host), no collision with `message-brokers/kafka`'s `9092`–`9094` or `communication-protocols/grpc`'s `9090`.
- Prometheus → `9096` (no collision with `noSQL/mongodb`'s `9095`).
- Grafana → `3003` (no collision with `noSQL/mongodb`'s `3002`).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (inherited from `noSQL/pom.xml` parent)
**Java:** 21

**`cassandra-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-data-cassandra`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test) — same set as `mongodb-demo` with `data-cassandra` swapped in for `data-mongodb`.

`application.yml`:
```yaml
spring:
  cassandra:
    contact-points: cassandra1:9042,cassandra2:9042,cassandra3:9042
    local-datacenter: datacenter1
    keyspace-name: ecommerce
    schema-action: none   # tables are created by cassandra-init, not the app

server:
  port: 8085
```

Same as the MongoDB module's `/etc/hosts` requirement, the app connects to the cluster using the containers' hostnames (`cassandra1`/`cassandra2`/`cassandra3`), so the README carries the same one-time host-setup step pointing those hostnames at `127.0.0.1`.

## README

`noSQL/cassandra/README.md` follows `noSQL/mongodb/README.md`'s structure exactly: prerequisites (Java 21, Maven, Docker, `/etc/hosts` entries), start-the-cluster instructions with a ring-status check (`nodetool status` instead of `rs.status()`), run-the-app instructions, `curl` walkthroughs for all ten endpoints grouped by pattern, a cluster-topology mermaid diagram, a patterns-and-data-flow mermaid diagram, a table characterizing tables/patterns (parallel to MongoDB's "Collection characteristics" table), monitoring section (Prometheus/Grafana URLs, dashboard panels), ring admin commands (`nodetool status`, `nodetool ring`, `cqlsh` inspection), and stop-the-cluster instructions.

## Scope limits

- No multi-datacenter topology — single datacenter (`datacenter1`), matching the demo's scope (this is about Cassandra's data-modeling and consistency primitives, not full operational topology).
- No repair/anti-entropy demo (`nodetool repair`) — out of scope; the README may mention it exists without demonstrating it.
- The raw-`CqlSession` exception is confined to `lwt/`, `consistency/`, `ttl/`, and `counter/`; `crud/` and `datamodeling/` use `CassandraTemplate`/Spring Data Cassandra idioms throughout.
- No embedded Cassandra or Testcontainers substitute is introduced for `mvn test` — unit tests mock `CassandraTemplate` and `CqlSession`/`ResultSet`, keeping this module consistent with `noSQL/mongodb`'s testing convention rather than introducing a new one.
- If the `jmx_exporter`-javaagent monitoring setup proves unreliable during implementation, it falls back to instrumenting `cassandra1` only rather than being dropped entirely — confirm this fallback with the user only if it's actually needed.
